package com.johncorby.gravityGuild2

import net.kyori.adventure.util.TriState
import org.battleplugins.arena.Arena
import org.battleplugins.arena.ArenaPlayer
import org.battleplugins.arena.competition.LiveCompetition
import org.battleplugins.arena.competition.map.LiveCompetitionMap
import org.battleplugins.arena.competition.phase.CompetitionPhaseType
import org.battleplugins.arena.event.ArenaEventHandler
import org.battleplugins.arena.event.arena.ArenaPhaseCompleteEvent
import org.battleplugins.arena.event.arena.ArenaPhaseStartEvent
import org.battleplugins.arena.event.player.ArenaKillEvent
import org.battleplugins.arena.event.player.ArenaRespawnEvent
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.*
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector


/*
 * IDEAS:
 * teleport killer to player on kill like v1?
 *
 * let players have more air control, while still requiring them to be grounded
 *      higher gravity? limited rockets per air? higher knockback from skull/fireball?
 *      tunnel via breaking blocks while gliding?
 * bigger radius for wither skulls? and higher knockback from above
 * tnt left click to throw
 * armor that does something (e.g. small durability but resistant to arrows)
 * snow in ground, tnt too maybe occasionally. this means ability to break blocks instead of shoot skull
 *
 * cooldown on bow shots? overpowered
 *
 * wind charges work really well. explosion on arrow is more forgiving then insta kill perfect aim. IDEAS after that:
 *
 * arrows have no gravity, weaker explosion. tnt have gravity like grenade, stronger explosion
 * bigger hitbox for flying players so you can hit them. but they stick to walls to go up, so explosion damage makes them more vulnerable there too
 * custom wind charge hit response (manually set velocity). rn its kinda finicky. I HAVE NO IDEA HOW TO DO THIS CURRENTLY
 * notify on reach lethal velocity for wall/floor hit? or remove that entirely
 * arrow explode strength stronger for faster arrow
 * custom killer tracker: any hit you get on them and then they die will count within n seconds of their hit. maybe track who did the most damage if theres multiple
 * move wind charge to separate item to enforce switch?
 */

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
        }

    }

    @ArenaEventHandler
    fun PlayerInteractEvent.handler() {
        // only left click works.
        // BUG: left click air erroneously happens with other stuff like throwing items. it's fine
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.LEFT_CLICK_AIR) return

        // only work when holding mace because funny melee
        if (player.inventory.itemInMainHand != Items.mace) return

        // shoot wind charge. it has the most fun movement. if its too OP, use fireball
        player.launchProjectile(WindCharge::class.java, player.fixedVelocity.add(player.eyeLocation.direction))
        // cancel so player doesnt break anything
        isCancelled = true
    }

    @ArenaEventHandler
    fun EntityDamageEvent.handler() {
        plugin.logger.info("${entity.name} lost $damage health from $cause")

        // do not allow you to blow yourself up :)
        if (cause == DamageCause.ENTITY_EXPLOSION && entity == damageSource.causingEntity) {
            isCancelled = true
            return
        }

        // do this only for hit ground and wall. everything else should be normal damage
        if (
            cause != DamageCause.FALL &&
            cause != DamageCause.FLY_INTO_WALL
        ) return

        // delete fall damage
        //if (
        //    cause == DamageCause.FALL ||
        //    cause == DamageCause.FLY_INTO_WALL
        //) isCancelled = true

        // revert non-lethal damage
        // TODO: should we actually do this? or only revert some damage types like older versions?
        if ((entity as Player).health - damage > 0) damage = 0.0
    }

    // food level change prevented in config. could change this? its nice to not deal with hunger

    @ArenaEventHandler
    fun ArenaPhaseStartEvent.handler() {
        if (phase.type == CompetitionPhaseType.INGAME) {
            val team = Bukkit.getScoreboardManager().mainScoreboard.getTeam("GravityGuild")!!

            (competition as LiveCompetition).players.forEach { player ->
                player.player.initAndSpawn()

                team.addPlayer(player.player)
            }
        }
    }

    @ArenaEventHandler
    fun ArenaPhaseCompleteEvent.handler() {
        if (phase.type == CompetitionPhaseType.VICTORY) {
            // arena restore puts back entities, so lets remove current ones
            val map = competition.map as LiveCompetitionMap
            val bounds = map.bounds!!
            for (entity in map.world.entities) {
                if (bounds.isInside(entity.boundingBox) && entity !is Player) {
                    entity.remove()
                }
            }
        } else if (phase.type == CompetitionPhaseType.INGAME) {
            val team = Bukkit.getScoreboardManager().mainScoreboard.getTeam("GravityGuild")!!

            (competition as LiveCompetition).players.forEach { player ->
                team.removePlayer(player.player)
            }
        }
    }

    @ArenaEventHandler
    fun ArenaRespawnEvent.handler() {
        if (competition.phase == CompetitionPhaseType.INGAME) {
//            player.initAndSpawn()
        }

        // cooldown

    }

    object Items {
        val bow = ItemStack.of(Material.BOW).apply {
            addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)
            addUnsafeEnchantment(Enchantment.INFINITY, 1)
        }
        val arrow = ItemStack.of(Material.ARROW)
        val tnt = ItemStack.of(Material.TNT)
        val mace = ItemStack.of(Material.MACE).apply {
            addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)
        }
        val helmet = ItemStack.of(Material.END_ROD).apply {
            addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1)
        }
        val chestplate = ItemStack.of(Material.ELYTRA).apply {
            addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1)
            addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)
        }
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

    fun Player.initAndSpawn() {
//        addPotionEffect(PotionEffect(PotionEffectType.NIGHT_VISION, Int.MAX_VALUE, 1, false, false))

        // init inventory
        // should be empty at this point
//        inventory.setItem(0, Items.item0)
//        inventory.setItem(1, Items.item1)
        inventory.addItem(Items.bow)
//        inventory.addItem(Items.tnt)
        inventory.addItem(Items.mace)
        inventory.addItem(Items.arrow)
        inventory.helmet = Items.helmet
        inventory.chestplate = Items.chestplate

        // TODO: teleport to random part on the map? or just use manual spawns like currently

    }

    // TODO: cooldown on death
    // TODO: hide player nametags

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