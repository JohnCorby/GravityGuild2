package com.johncorby.gravityGuild2

import com.johncorby.gravityGuild2.ArrowTracker.startTracking
import com.johncorby.gravityGuild2.ArrowTracker.stopTracking
import net.kyori.adventure.util.TriState
import org.battleplugins.arena.Arena
import org.battleplugins.arena.ArenaPlayer
import org.battleplugins.arena.competition.LiveCompetition
import org.battleplugins.arena.competition.map.LiveCompetitionMap
import org.battleplugins.arena.competition.phase.CompetitionPhaseType
import org.battleplugins.arena.event.ArenaEventHandler
import org.battleplugins.arena.event.arena.ArenaPhaseCompleteEvent
import org.battleplugins.arena.event.arena.ArenaPhaseStartEvent
import org.battleplugins.arena.event.player.ArenaRespawnEvent
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


/*
 * IDEAS:
 * teleport killer to player on kill like v1?
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

    @ArenaEventHandler
    fun ProjectileLaunchEvent.handler() {
        when (entity) {
            is Arrow -> {
                entity.setGravity(false)
                entity.visualFire = TriState.TRUE
                (entity as Arrow).startTracking()
            }

            is Snowball -> {
                entity.isGlowing = true;
                entity.visualFire = TriState.TRUE
            }
        }
    }

    @ArenaEventHandler
    fun ProjectileHitEvent.handler() {
        when (entity) {
            // arrow kills
            is Arrow -> {
                (hitEntity as? Damageable)?.damage(9999.0)
                (entity as Arrow).stopTracking()
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
        // BUG: left click air erroneously happens with other stuff like throwing items
        if (action != Action.LEFT_CLICK_BLOCK) return

        // shoot skull
        // TODO: use fireball instead of wither skull? be manual rocket jump?
        // TODO: use wind charge instead????
        player.launchProjectile(WitherSkull::class.java, player.eyeLocation.direction)
        // cancel so player doesnt break anything
        isCancelled = true
    }

    @ArenaEventHandler
    fun EntityDamageEvent.handler() {
        plugin.logger.info("${entity.name} damaged $damage health by $cause")

        // prevent wither skull from causing wither :P
        // TODO: this cause stuff is too general and sucks
        if (cause == DamageCause.PROJECTILE) {
            isCancelled = true
            return
        }

        // let other most damage types thru
        if (
            cause != DamageCause.FALL &&
            cause != DamageCause.FLY_INTO_WALL &&
            cause != DamageCause.ENTITY_EXPLOSION
        ) return

        // revert non-lethal damage
        // TODO: should we actually do this? or only revert some damage types like older versions?
        if ((entity as Player).health - damage > 0) damage = 0.0
    }

    // food level change prevented in config. could change this?

    @ArenaEventHandler
    fun ArenaPhaseStartEvent.handler() {
        if (phase.type == CompetitionPhaseType.INGAME) {
//            val team = Bukkit.getScoreboardManager().mainScoreboard.getTeam("GravityGuild")!!

            (competition as LiveCompetition).players.forEach { player ->
                player.player.initAndSpawn()

//                team.addPlayer(player.player)
            }
        }
    }

    @ArenaEventHandler
    fun ArenaPhaseCompleteEvent.handler() {
        // arena restore puts back entities, so lets remove current ones
        if (phase.type == CompetitionPhaseType.VICTORY) {
            val map = competition.map as LiveCompetitionMap
            val bounds = map.bounds!!
            for (entity in map.world.entities) {
                if (bounds.isInside(entity.boundingBox) && entity !is Player) {
                    entity.remove()
                }
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

    @ArenaEventHandler
    fun PlayerDeathEvent.handler() {
        // dont drop the custom items
//        drops.remove(Items.bow)
//        drops.remove(Items.arrow)
//        drops.remove(Items.helmet)
//        drops.remove(Items.chestplate)
    }

    fun Player.initAndSpawn() {
//        addPotionEffect(PotionEffect(PotionEffectType.NIGHT_VISION, Int.MAX_VALUE, 1, false, false))

        // init inventory
        // should be empty at this point
//        inventory.setItem(0, Items.item0)
//        inventory.setItem(1, Items.item1)
        inventory.addItem(Items.bow)
        inventory.addItem(Items.mace)
        inventory.addItem(Items.arrow)
        inventory.helmet = Items.helmet
        inventory.chestplate = Items.chestplate

        // TODO: teleport to random part on the map? or just use manual spawns like currently

    }

    // TODO: cooldown on death
    // TODO: hide player nametags
}