package com.johncorby.gravityGuild2

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent
import com.johncorby.gravityGuild2.ArrowTracker.startTracking
import com.johncorby.gravityGuild2.ArrowTracker.stopTracking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.util.TriState
import org.battleplugins.arena.Arena
import org.battleplugins.arena.ArenaPlayer
import org.battleplugins.arena.competition.LiveCompetition
import org.battleplugins.arena.competition.map.LiveCompetitionMap
import org.battleplugins.arena.competition.phase.CompetitionPhaseType
import org.battleplugins.arena.competition.phase.phases.IngamePhase
import org.battleplugins.arena.event.ArenaEventHandler
import org.battleplugins.arena.event.arena.ArenaPhaseCompleteEvent
import org.battleplugins.arena.event.arena.ArenaPhaseStartEvent
import org.battleplugins.arena.event.player.ArenaJoinEvent
import org.battleplugins.arena.event.player.ArenaKillEvent
import org.battleplugins.arena.event.player.ArenaLeaveEvent
import org.battleplugins.arena.event.player.ArenaRespawnEvent
import org.battleplugins.arena.stat.ArenaStats
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.*
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.entity.EntityToggleGlideEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemDamageEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.scoreboard.Team
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f

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
                (entity as Arrow).startTracking()
            }

            is Snowball -> {
                entity.isGlowing = true
                entity.visualFire = TriState.TRUE
            }

            is Trident -> {
                isCancelled = true
            }
        }
    }

    @ArenaEventHandler
    fun ProjectileHitEvent.handler() {
        when (entity) {
            // arrow kills
            is Arrow -> {
//                plugin.logger.info("arrow vel = ${entity.velocity.length()}")


//                (hitEntity as? Damageable)?.damage(9999.0)
                (entity as Arrow).stopTracking()
//                (entity as Arrow).damage = 0.0
                val power = this.entity.velocity.length().remap(0.3, 3.0, 0.0, 3.0).toFloat()
                entity.world.createExplosion(entity, power, false)
                entity.remove() // dont stick
            }

            // death snowball
            is Snowball -> {
                (hitEntity as? Damageable)?.damage(9999.0, entity.shooter as Player)
                entity.world.strikeLightningEffect(entity.location)
            }

            is WindCharge -> {
//                plugin.logger.info("cancelling wind charge")
//                isCancelled = true
            }

            is EnderPearl -> {
                val display = entity.passengers.firstOrNull() as? BlockDisplay ?: return
                isCancelled = true
                entity.world.createExplosion(
                    entity,
                    if (display.transformation.scale == Vector3f(.5f)) 2f else 5f,
                    true
                )
                display.remove()
                entity.remove()
            }
        }

    }

    @ArenaEventHandler
    fun PlayerInteractEvent.handler() {
        // BUG: off hand exists :P
        when (player.inventory.itemInMainHand) {
            Items.MACE.item -> {
                // only left click works.
                // BUG: left click air erroneously happens with other stuff like throwing items. it's fine
                if (action.isLeftClick) {
                    // shoot wind charge. it has the most fun movement. if its too OP, use fireball
                    player.launchProjectile(WindCharge::class.java, player.fixedVelocity.add(player.eyeLocation.direction))
                    // cancel so player doesnt break anything
                    //        isCancelled = true

                } else if (action.isRightClick) {

                    plugin.logger.info("mace vel speed = ${player.fixedVelocity.length()}")
                    if (
//                        player.fallDistance > 5
                        player.fixedVelocity.length() > 1
                    ) {
                        val nearbyEntities = player.world.getNearbyEntities(
                            // like airblast, check in front of where we're looking
                            player.eyeLocation.add(player.eyeLocation.direction.multiply(3)),
                            3.0, 3.0, 3.0,
                            { it is Damageable && it != player }
                        )
                        if (nearbyEntities.isNotEmpty()) {
                            plugin.logger.info("mace HIT")

                            // mimic mace effect but bigger radius
                            player.isGliding = false
                            player.velocity = player.velocity.multiply(-1.5)
                            nearbyEntities.forEach { (it as Damageable).damage(20.0, player) }
                            player.world.strikeLightningEffect(player.location)
                            player.fallDistance = 0f
                            player.world.playSound(player, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 1f)

                        } else {
                            player.world.playSound(player, Sound.ITEM_MACE_SMASH_AIR, 1f, 1f)
                            plugin.logger.info("mace miss")
                        }
                    }
                }
            }

            Items.TNT.item -> {
                if (action == Action.PHYSICAL) return
                if (player in dontTnt) {
                    player.world.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, .5f)
                    return
                }
                val small = action.isRightClick

                val projectile = player.launchProjectile(EnderPearl::class.java, player.fixedVelocity.add(player.eyeLocation.direction.multiply(.7)))
                val tnt = projectile.world.spawn(projectile.location, BlockDisplay::class.java)
                tnt.block = Material.TNT.createBlockData()
                tnt.transformation = Transformation(
                    Vector3f(-.5f).mul(if (small) .5f else 1f),
                    Quaternionf(),
                    Vector3f(1f).mul(if (small) .5f else 1f),
                    Quaternionf()
                )
                projectile.addPassenger(tnt)

                dontTnt.add(player)
                Bukkit.getScheduler().runTaskLater(plugin, Runnable { dontTnt.remove(player) }, 20 * 5)
                player.world.playSound(player, Sound.ENTITY_TNT_PRIMED, 1f, if (small) 1f else .5f)

            }

            Items.SALMON.item -> {
                if (!action.isLeftClick) return

                val nearbyEntities = player.world.getNearbyEntities(
                    player.eyeLocation.add(player.eyeLocation.direction.multiply(3)),
                    3.0, 3.0, 3.0,
                    { it is Damageable && it != player }
                )
                nearbyEntities.forEach { player.attack(it) }
            }
        }
    }

    @ArenaEventHandler
    fun EntityDamageEvent.handler() {
        if (this is EnderPearl &&
            entity.passengers.firstOrNull() is BlockDisplay) {
            // rn nothing should be able to stop our grenade
            isCancelled = true
            return
        }

        // fish moment
        if (this is EntityDamageByEntityEvent &&
            this.damager is Player &&
            (this.damager as Player).inventory.itemInMainHand == Items.SALMON.item) {
            // FISH
            this.entity.velocity = this.damager.location.subtract(this.entity.location).direction.multiply(20)
        }

        if (this.entity !is Player) return; // entity damage by entity can have not player be damaged by player

        plugin.logger.info("${entity.name} lost $damage health from $cause")

        // TEMP: do not allow you to blow yourself up
//        if (cause == DamageCause.ENTITY_EXPLOSION && entity == damageSource.causingEntity) {
//            isCancelled = true
//            return
//        }

        // revert non lethal damage only for hit ground and wall. everything else should be normal damage
        if (cause == DamageCause.FALL || cause == DamageCause.FLY_INTO_WALL) {
            // revert non-lethal damage
            if ((entity as Player).health - damage > 0) damage = 0.0
        }

        if (cause == DamageCause.VOID) damage = 9999.0 // just fuckin kill em

        val damagingPlayer = this.damageSource.causingEntity as? Player
        if (damagingPlayer != null && damagingPlayer != entity) {
            // damaged by other player. track this
            playerLastDamager[entity as Player] = damagingPlayer to Bukkit.getCurrentTick()
            plugin.logger.info("tracking ${entity.name} got damaged by ${damagingPlayer.name} at ${Bukkit.getCurrentTick()}")

            // tf2 moment teehee
            damagingPlayer.playSound(damagingPlayer, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
        }
    }

    // food level change prevented in config. could change this? its nice to not deal with hunger

    @ArenaEventHandler
    fun ArenaPhaseStartEvent.handler() {
        when (phase.type) {
            CompetitionPhaseType.INGAME -> {
                (competition as LiveCompetition).players.forEach { player ->
                    player.player.initInventory()

                    // scoreboard module adds us to its own non global scoreboard. I THINK we need to add the team to THAT one to get the nametag thing working
                    // this gets removed on remove-scoerboard so hopefully thatll remove the team effects
//                    val team = player.player.scoreboard.getTeam("GravityGuild") ?: player.player.scoreboard.registerNewTeam("GravityGuild")
//                    team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER)
//                    team.addPlayer(player.player)
                }
            }
        }
    }

    @ArenaEventHandler
    fun ArenaPhaseCompleteEvent.handler() {
        when (phase.type) {
            CompetitionPhaseType.INGAME -> {

                (competition as LiveCompetition).players.forEach { player ->
//                    val team = player.player.scoreboard.getTeam("GravityGuild")!!
//                    team.removePlayer(player.player)
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
            Bukkit.broadcast(
                Component.text("Arena ${this.competition.map.name} has someone in it! Click to join")
                    .color(NamedTextColor.GOLD)
                    .clickEvent(ClickEvent.runCommand("/gg join ${this.competition.map.name}"))
            )
        }

        player.saturation = 9999f
        player.saturatedRegenRate = 20
        player.unsaturatedRegenRate = 20

        // if everyone is in, just start the game
        if (this.competition.players.count() == Bukkit.getOnlinePlayers().count())
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                this.competition.phaseManager.setPhase(CompetitionPhaseType.INGAME, true)
            }, 10);
    }

    @ArenaEventHandler
    fun ArenaLeaveEvent.handler() {
//        val team = Bukkit.getScoreboardManager().mainScoreboard.getTeam("GravityGuild")!!
//        team.removePlayer(player)

        dontTnt.remove(player)
        dontGlide.remove(player)

        playerLastDamager.remove(player)

        // because our victory condition is only time limit, it doesnt close early. we gotta do this ourselves
        if (this.competition.players.count() == 0) {
            this.competition.phaseManager.setPhase(CompetitionPhaseType.VICTORY, true)
        }
    }

    @ArenaEventHandler
    fun ArenaRespawnEvent.handler() {
        when (competition.phase) {
            CompetitionPhaseType.INGAME -> {
                //            player.initAndSpawn()
            }
        }

        dontTnt.remove(player)
        dontGlide.remove(player)

        // okay, now use our custom killer thing to track kills
        // TODO: maybe move this to PlayerDeathEvent if we wait before respawning
        playerLastDamager[player]?.let { lastDamager ->
            plugin.logger.info("${player.name} last damaged by ${lastDamager.first.name} at ${lastDamager.second} (now is ${Bukkit.getCurrentTick()})")

            if (Bukkit.getCurrentTick() - lastDamager.second < 20 * 5) { // did it recently enough, give em the kill
                ArenaPlayer.getArenaPlayer(lastDamager.first)?.computeStat(ArenaStats.KILLS) { old -> (old ?: 0) + 1 }
                Bukkit.broadcast(Component.text("Kill credit goes to ${lastDamager.first.name}").color(NamedTextColor.GRAY))


                // tf2 moment teehee
                lastDamager.first.playSound(lastDamager.first, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)

                // v1 behavior: teleport killer to killed
//                player.teleport(lastDamager.first)
            }
        }

        playerLastDamager.remove(player) // stop tracking who last hurt them becasue they are dead
//        playerLastDamager.getOrPut(player) { mutableMapOf() }.clear()
    }

    @ArenaEventHandler
    fun PlayerPostRespawnEvent.handler() {

        player.saturation = 9999f
        player.saturatedRegenRate = 20
        player.unsaturatedRegenRate = 20

        // TODO: cooldown?
    }

    //    data class PlayerDamageData(var lastDamagedTick: Int, var totalDamage: Double)

    val playerLastDamager = mutableMapOf<Player, Pair<Player, Int>>()

    @ArenaEventHandler
    fun ArenaKillEvent.handler() {
        // cancel out kill increment that StatListener did. we do our own thing on respawn
        this.killer.computeStat(ArenaStats.KILLS) { old: Int? -> (old ?: 0) - 1 }
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
        SALMON(ItemStack.of(Material.SALMON).apply {
            // BUG: knockback doesnt work sometimes?
//            addUnsafeEnchantment(Enchantment.KNOCKBACK, 9999)
            addUnsafeEnchantment(Enchantment.FIRE_ASPECT, 2)
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

    fun Player.initInventory() {
//        addPotionEffect(PotionEffect(PotionEffectType.NIGHT_VISION, Int.MAX_VALUE, 1, false, false))

        // init inventory
        // should be empty at this point
//        inventory.setItem(0, Items.item0)
//        inventory.setItem(1, Items.item1)
        inventory.addItem(Items.BOW.item)
        inventory.addItem(Items.TNT.item)
        inventory.addItem(Items.SALMON.item)
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

    val dontTnt = mutableSetOf<Player>();

    @ArenaEventHandler
    fun EntityToggleGlideEvent.handler() {
        if (isGliding && entity in dontGlide) {
            val player = entity as Player
            player.world.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, .5f)
            isCancelled = true
        }
//        (entity as Attributable).getAttribute(Attribute.SCALE)!!.apply {
//            baseValue = if (isGliding) 3.0 else defaultValue
//        }
    }
}

// dont feel like having custom competition logic so ill just track stuff globally and make sure to clear it out
// if you leave and join an arena really quickly youll get cleared out of this, but thats really unlikely
val dontGlide = mutableSetOf<Player>()
