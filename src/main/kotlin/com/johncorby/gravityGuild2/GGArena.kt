package com.johncorby.gravityGuild2

import net.kyori.adventure.util.TriState
import org.battleplugins.arena.Arena
import org.battleplugins.arena.ArenaPlayer
import org.battleplugins.arena.event.ArenaEventHandler
import org.bukkit.entity.Arrow
import org.bukkit.entity.Damageable
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.entity.WitherSkull
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.player.PlayerInteractEvent


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
        if (entity !is Arrow) return

        entity.setGravity(false)
        entity.visualFire = TriState.TRUE
//        (entity as Arrow).startTracking()BlockProjectileSource
    }

    @ArenaEventHandler
    fun ProjectileHitEvent.handler() {
        when (entity) {
            is Arrow -> {
                (hitEntity as? Damageable)?.damage(9999.0)
//                (entity as Arrow).stopTracking()
                entity.remove()
            }

            is Snowball -> {
                // death snowball
                (hitEntity as? Player)?.damage(9999.0)
                entity.world.strikeLightningEffect(entity.location)
            }
        }

    }

    @ArenaEventHandler
    fun PlayerInteractEvent.handler() {
        if (action == Action.PHYSICAL) return

        // shoot skull
        player.launchProjectile(WitherSkull::class.java, player.eyeLocation.direction)
        // cancel so player doesnt break anything
        isCancelled = true
    }

    @ArenaEventHandler
    fun EntityDamageEvent.handler() {
        // only immune to fall damage and wither skull explosion
        if (
            cause != DamageCause.FALL &&
            cause != DamageCause.FLY_INTO_WALL &&
            cause != DamageCause.ENTITY_EXPLOSION
        ) return

        // revert non-lethal damage
        if ((entity as Player).health - damage > 0) damage = 0.0
    }

    fun FoodLevelChangeEvent.handler() {
        isCancelled = true
    }

    // TODO read v1 v2 v3 code and try to make it nice
    // TODO give items. could use config, but thats more work
}