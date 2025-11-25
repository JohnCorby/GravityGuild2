package com.johncorby.gravityGuild2

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.util.TriState
import org.battleplugins.arena.Arena
import org.battleplugins.arena.ArenaPlayer
import org.battleplugins.arena.competition.LiveCompetition
import org.battleplugins.arena.competition.map.LiveCompetitionMap
import org.battleplugins.arena.competition.phase.CompetitionPhaseType
import org.battleplugins.arena.event.ArenaEventHandler
import org.battleplugins.arena.event.arena.ArenaPhaseCompleteEvent
import org.battleplugins.arena.event.arena.ArenaPhaseStartEvent
import org.battleplugins.arena.event.player.ArenaJoinEvent
import org.battleplugins.arena.event.player.ArenaLeaveEvent
import org.battleplugins.arena.event.player.ArenaRespawnEvent
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.*
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemDamageEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector

class GGArena : Arena() {
    override fun createCommandExecutor() = GGCommandExecutor(this)

    init {
        eventManager.registerArenaResolver(ProjectileLaunchEvent::class.java) { event ->
            ArenaPlayer.getArenaPlayer(event.entity.shooter as? Player)?.competition
        }
        eventManager.registerArenaResolver(ProjectileHitEvent::class.java) { event ->
            ArenaPlayer.getArenaPlayer(event.entity.shooter as? Player)?.competition
        }
    }

    @Suppress("DEPRECATION")
    val Player.fixedVelocity get() = if (isOnGround) Vector(0, 0, 0) else velocity

    @ArenaEventHandler
    fun ProjectileLaunchEvent.handler() {
//        plugin.logger.info("${entity.shooter} shoot ${entity}\nshooter vel = ${(entity.shooter as Player).fixedVelocity}")

        // dont include player velocity in projectile velocity
        entity.velocity = entity.velocity.subtract((entity.shooter as Player).fixedVelocity)

        when (entity) {
            is Arrow -> {
                entity.setGravity(false)
                entity.visualFire = TriState.TRUE
//                val tnt = entity.world.spawn(entity.location, TNTPrimed::class.java)
//                entity.addPassenger(tnt)
//                (entity as Arrow).startTracking()
            }

            is Snowball -> {
                entity.isGlowing = true
                entity.visualFire = TriState.TRUE
            }
        }
    }

    @ArenaEventHandler
    fun ProjectileHitEvent.handler() {
        when (entity) {
            // arrow kills
            is Arrow -> {
//                (hitEntity as? Damageable)?.damage(9999.0)
//                (entity as Arrow).stopTracking()
//                (entity as Arrow).damage = 0.0
                entity.world.createExplosion(entity, 2f, false)
                entity.remove() // dont stick
            }

            // death snowball
            is Snowball -> {
                (hitEntity as? Damageable)?.damage(9999.0)
                entity.world.strikeLightningEffect(entity.location)
            }

            is WindCharge -> {
                plugin.logger.info("cancelling wind charge")
//                isCancelled = true
            }
        }

    }

    @ArenaEventHandler
    fun PlayerInteractEvent.handler() {

        // only work when holding mace because funny melee
        when {
            player.holdingItem(Items.MACE.item) -> {
                // only left click works.
                // BUG: left click air erroneously happens with other stuff like throwing items. it's fine
                if (action != Action.LEFT_CLICK_BLOCK && action != Action.LEFT_CLICK_AIR) return

                var didMace = false;
                plugin.logger.info("vel = ${player.velocity}")
                if (
                    player.velocity.y < -1 &&
                    !player.isGliding
                ) {
                    val nearbyEntities = player.world.getNearbyEntities(player.location, 3.0, 3.0, 3.0)
                        .filter { it is Damageable && it !is Player }
                    if (nearbyEntities.isNotEmpty()) {

                        // mimic mace effect but bigger radius
                        player.velocity = player.velocity.multiply(-1)
                        nearbyEntities.forEach { (it as Damageable).damage(99.0, player) }
                        player.world.strikeLightningEffect(player.location)
                        player.fallDistance = 0f
                        player.playSound(player, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 1f)

                    } else {
                        player.playSound(player, Sound.ITEM_MACE_SMASH_AIR, 1f, 1f)

                    }
                    didMace = true
                }

                if (!didMace) {
                    // shoot wind charge. it has the most fun movement. if its too OP, use fireball
                    player.launchProjectile(WindCharge::class.java, player.fixedVelocity.add(player.eyeLocation.direction))
                    // cancel so player doesnt break anything
                    //        isCancelled = true
                }
            }

            player.holdingItem(Items.TNT.item) -> {
                if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return

                com.johncorby.gravityGuild2.plugin.logger.info("shoot tnt")

//                player.launchProjectile(TNTPrimed::class.java, player.fixedVelocity.add(player.eyeLocation.direction.multiply(0.3)))
            }
        }
    }

    @ArenaEventHandler
    fun EntityDamageEvent.handler() {
        plugin.logger.info("${entity.name} lost $damage health from $cause")

        // TEMP: do not allow you to blow yourself up
        if (cause == DamageCause.ENTITY_EXPLOSION && entity == damageSource.causingEntity) {
            isCancelled = true
            return
        }

        // revert non lethal damage only for hit ground and wall. everything else should be normal damage
        if (cause == DamageCause.FALL || cause == DamageCause.FLY_INTO_WALL) {
            // revert non-lethal damage
            if ((entity as Player).health - damage > 0) damage = 0.0
        }
    }

    // food level change prevented in config. could change this? its nice to not deal with hunger

    @ArenaEventHandler
    fun ArenaPhaseStartEvent.handler() {
        when (phase.type) {
            CompetitionPhaseType.INGAME -> {
                (competition as LiveCompetition).players.forEach { player ->
                    player.player.initInventory()
                }
            }
        }
    }

    @ArenaEventHandler
    fun ArenaPhaseCompleteEvent.handler() {
        when (phase.type) {
            CompetitionPhaseType.INGAME -> {
                val team = Bukkit.getScoreboardManager().mainScoreboard.getTeam("GravityGuild")!!

                (competition as LiveCompetition).players.forEach { player ->
                    team.removePlayer(player.player)
                }
            }

            CompetitionPhaseType.VICTORY -> {
                // arena restore puts back entities, so lets remove current ones
                val map = competition.map as LiveCompetitionMap
                val bounds = map.bounds!!
                for (entity in map.world.entities) {
                    if (bounds.isInside(entity.boundingBox) && entity !is Player) {
                        entity.remove()
                    }
                }
            }
        }
    }

    @ArenaEventHandler
    fun ArenaJoinEvent.handler() {
        if (this.competition.players.count() == 1) {
            plugin.server.broadcast(
                Component.text("Arena ${this.competition.map.name} has someone in it! Click to join")
                    .color(NamedTextColor.GOLD)
                    .clickEvent(ClickEvent.runCommand("/gg join ${this.competition.map.name}"))
            )
        }

        val team = Bukkit.getScoreboardManager().mainScoreboard.getTeam("GravityGuild")!!
        team.addPlayer(player)
    }

    @ArenaEventHandler
    fun ArenaLeaveEvent.handler() {
        val team = Bukkit.getScoreboardManager().mainScoreboard.getTeam("GravityGuild")!!
        team.removePlayer(player)
    }

    @ArenaEventHandler
    fun ArenaRespawnEvent.handler() {
        when (competition.phase) {
            CompetitionPhaseType.INGAME -> {
                //            player.initAndSpawn()
            }
        }

        // TODO cooldown

    }

    enum class Items(val item: ItemStack) {
        BOW(ItemStack.of(Material.BOW).apply {
            addUnsafeEnchantment(Enchantment.INFINITY, 1)
            addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)
            addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1)
        }),
        ARROW(ItemStack.of(Material.ARROW).apply {
            addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)
            addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1)
        }),
        TNT(ItemStack.of(Material.TNT).apply {
            addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)
            addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1)
        }),
        MACE(ItemStack.of(Material.MACE).apply {
            addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)
            addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1)
        }),
        TRIDENT(ItemStack.of(Material.MACE).apply {
            addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)
            addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1)
        }),
        HELMET(ItemStack.of(Material.END_ROD).apply {
            addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)
            addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1)
        }),
        CHESTPLATE(ItemStack.of(Material.ELYTRA).apply {
            addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)
            addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1)
        });
    }

//    @ArenaEventHandler
//    fun PlayerDeathEvent.handler() {
    // dont drop the custom items
//        drops.remove(Items.bow)
//        drops.remove(Items.arrow)
//        drops.remove(Items.helmet)
//        drops.remove(Items.chestplate)
//    }

    // v1 behavior: teleport killer to killed
//    @ArenaEventHandler
//    fun ArenaKillEvent.handler() {
//        killer.player.teleport(killed.player)
//    }

    fun Player.initInventory() {
//        addPotionEffect(PotionEffect(PotionEffectType.NIGHT_VISION, Int.MAX_VALUE, 1, false, false))

        // init inventory
        // should be empty at this point
//        inventory.setItem(0, Items.item0)
//        inventory.setItem(1, Items.item1)
        inventory.addItem(Items.BOW.item)
        inventory.addItem(Items.TNT.item)
        inventory.addItem(Items.MACE.item)
        inventory.addItem(Items.ARROW.item)
        inventory.helmet = Items.HELMET.item
        inventory.chestplate = Items.CHESTPLATE.item

        // TODO: teleport to random part on the map? or just use manual spawns like currently
    }

    fun Player.holdingItem(item: ItemStack) = inventory.itemInMainHand == item || inventory.itemInOffHand == item

    @ArenaEventHandler
    fun PlayerDropItemEvent.handler() {
        // dont let players drop our items
        if (Items.entries.any { it.item == itemDrop.itemStack })
            isCancelled = true
    }

    @ArenaEventHandler
    fun PlayerItemDamageEvent.handler() {
        if (Items.entries.any { it.item == this.item })
            isCancelled = true
    }

    @ArenaEventHandler
    fun BlockPlaceEvent.handler() {
        if (itemInHand == Items.TNT.item) {
            isCancelled = true
        }
    }

    /*
        @ArenaEventHandler
        fun EntityToggleGlideEvent.handler() {
            if (isGliding) {
                entity.world.spawn(entity.location, Slime::class.java).apply {
                    setAI(false)
    //                isInvisible = true
                    size = 10
                    entity.addPassenger(this)
                }
            } else {
                entity.passengers[0].remove()
            }
        }
    */
}