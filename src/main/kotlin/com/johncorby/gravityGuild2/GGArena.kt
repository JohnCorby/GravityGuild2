package com.johncorby.gravityGuild2

import com.johncorby.gravityGuild2.ArrowTracker.startTracking
import com.johncorby.gravityGuild2.ArrowTracker.stopTracking
import net.kyori.adventure.util.TriState
import org.battleplugins.arena.Arena
import org.battleplugins.arena.ArenaPlayer
import org.battleplugins.arena.competition.Competition
import org.battleplugins.arena.competition.LiveCompetition
import org.battleplugins.arena.competition.phase.CompetitionPhaseType
import org.battleplugins.arena.event.ArenaEventHandler
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
        // only left click works. left click air erroneously happens with other stuff like throwing items
        if (action != Action.LEFT_CLICK_BLOCK) return

        // shoot skull
        // TODO: use fireball instead of wither skull? be manual rocket jump?
        player.launchProjectile(WitherSkull::class.java, player.eyeLocation.direction)
        // cancel so player doesnt break anything
        isCancelled = true
    }

    @ArenaEventHandler
    fun EntityDamageEvent.handler() {
        plugin.logger.info("$entity damaged by $cause")

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
        if (competition.phase == CompetitionPhaseType.INGAME) {
            player.initAndSpawn()
        }
    }

    object Items {
        val item0 = ItemStack(Material.BOW).apply {
            addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)
            addUnsafeEnchantment(Enchantment.INFINITY, 1)
        }
        val item1 = ItemStack(Material.ARROW)
        val helmet = ItemStack(Material.END_ROD).apply {
            addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1)
        }
        val chestplate = ItemStack(Material.ELYTRA).apply {
            addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1)
            addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)
        }
    }

    @ArenaEventHandler
    fun PlayerDeathEvent.handler() {
        drops.remove(Items.item0)
        drops.remove(Items.item1)
        drops.remove(Items.helmet)
        drops.remove(Items.chestplate)
    }

    fun Player.initAndSpawn() {
//        addPotionEffect(PotionEffect(PotionEffectType.NIGHT_VISION, Int.MAX_VALUE, 1, false, false))

        // init inventory
        // should be empty at this point
        inventory.setItem(0, Items.item0)
        inventory.setItem(1, Items.item1)
        inventory.helmet = Items.helmet
        inventory.chestplate = Items.chestplate

        // TODO: teleport to random part on the map

    }

    // TODO: cooldown on death

    // TODO: teleport killer to player on kill like v1?
}