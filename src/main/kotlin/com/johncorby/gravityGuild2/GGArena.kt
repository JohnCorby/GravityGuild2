package com.johncorby.gravityGuild2

import com.johncorby.gravityGuild2.ArrowTracker.startTracking
import com.johncorby.gravityGuild2.ArrowTracker.stopTracking
import net.kyori.adventure.util.TriState
import org.battleplugins.arena.Arena
import org.battleplugins.arena.ArenaPlayer
import org.battleplugins.arena.competition.LiveCompetition
import org.battleplugins.arena.competition.phase.CompetitionPhaseType
import org.battleplugins.arena.event.ArenaEventHandler
import org.battleplugins.arena.event.arena.ArenaPhaseStartEvent
import org.battleplugins.arena.event.player.ArenaJoinEvent
import org.battleplugins.arena.event.player.ArenaRespawnEvent
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.*
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack


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
        // only left click works
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) return

        // shoot skull
        // TODO: use fireball instead of wither skull? be manual rocket jump?
        player.launchProjectile(WitherSkull::class.java, player.eyeLocation.direction)
        // cancel so player doesnt break anything
        isCancelled = true
    }

    @ArenaEventHandler
    fun EntityDamageEvent.handler() {
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
            (competition as LiveCompetition).players.forEach { player -> player.player.initAndSpawn() }
        }
    }

    @ArenaEventHandler
    fun ArenaRespawnEvent.handler() {
        player.initAndSpawn()
    }

    fun Player.initAndSpawn() {
//        addPotionEffect(PotionEffect(PotionEffectType.NIGHT_VISION, Int.MAX_VALUE, 1, false, false))

        // init inventory
        inventory.apply {
            // inventory should be empty be this point
            addItem(
                ItemStack(Material.BOW).apply {
                    addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)
                    addUnsafeEnchantment(Enchantment.INFINITY, 1)
//                    addUnsafeEnchantment(Enchantment.FLAME, 1)
                },
                ItemStack(Material.ARROW)
            )
            helmet = ItemStack(Material.END_ROD).apply {
                addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1)
            }
            chestplate = ItemStack(Material.ELYTRA).apply {
                addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1)
                addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)
            }
        }

        // TODO: teleport to random part on the map

    }

    // TODO: cooldown on death

    // TODO: teleport killer to player on kill like v1?
}