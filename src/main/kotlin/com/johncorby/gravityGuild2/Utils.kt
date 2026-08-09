package com.johncorby.gravityGuild2

import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.battleplugins.arena.ArenaPlayer
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.BlockFace
import org.bukkit.damage.DamageSource
import org.bukkit.damage.DamageType
import org.bukkit.entity.Damageable
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.metadata.Metadatable
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import kotlin.random.Random


// use for movement cancel else it thinks ur falling slightly when ur not
val Player.velocityZeroGround get() = if (isOnGround) Vector(0, 0, 0) else velocity


var Player.isMarkedForDeath: Boolean
    get() = this.hasPotionEffect(PotionEffectType.GLOWING)
    set(value) {
        if (value) this.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, 10 * 20, 1, false, false, true))
        else this.removePotionEffect(PotionEffectType.GLOWING)
    }

var Player.dontGlide: Boolean
    get() = this.hasPotionEffect(PotionEffectType.SLOWNESS)
    set(value) {
        if (value) {
            this.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 20, 1, false, false, true))
            isGliding = false
            world.playSound(this, Sound.ENTITY_PLAYER_BURP, 1f, 1f)
        } else this.removePotionEffect(PotionEffectType.SLOWNESS)
    }

var Player.isRespawning: Boolean
    get() = hasPotionEffect(PotionEffectType.INVISIBILITY) && hasPotionEffect(PotionEffectType.NIGHT_VISION)
    set(value) {
        if (value) {
            // respawn
            val competition = ArenaPlayer.getArenaPlayer(this)!!.competition
            var spawns = competition.map.spawns!!.teamSpawns!!["Default"]!!.spawns!!
            spawns = spawns.filter { spawn ->
                val spawnLoc = spawn.toLocation(competition.map.world)
                // too close to other player
                if (competition.players.any { player -> player.player.location.distance(spawnLoc) < 20.0 }) return@filter false
                // head or feet inside block
                return@filter spawnLoc.block.isEmpty && spawnLoc.block.getRelative(BlockFace.UP).isEmpty
            }
            if (spawns.isEmpty()) spawns = competition.map.spawns!!.teamSpawns!!["Default"]!!.spawns!! // fallback
            val spawn = spawns[Random.nextInt(spawns.size)]
            this.teleport(spawn.toLocation(competition.map.world))

            clearActivePotionEffects()
            fireTicks = 0
            combatTracker.resetCombatState() // to reset death message

            // BUG: doesnt hide clothes
            addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, 20 * 3, 1, false, false, true))
            // just to get ur surroundings
            addPotionEffect(PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 3, 1, false, false, true))
        } else {
            removePotionEffect(PotionEffectType.INVISIBILITY)
            removePotionEffect(PotionEffectType.NIGHT_VISION)
        }
    }


fun Player.doItemCooldown(ticks: Int): Boolean {
    if (this.hasCooldown(inventory.itemInMainHand)) {
        this.world.playSound(this, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, .5f)
        return true
    }

    setCooldown(inventory.itemInMainHand, ticks)
    return false
}

fun Player.checkHitbox(radius: Double): Collection<Entity> = this.world.getNearbyEntities(
    this.eyeLocation.add(this.eyeLocation.direction.multiply(radius)),
    radius, radius, radius,
    { it != this }
)

fun Damageable.damagePrecise(amount: Double, source: Entity, player: Player) =
    damage(amount, DamageSource.builder(DamageType.GENERIC).withDirectEntity(source).withCausingEntity(player).build())


fun Float.remapClamped(
    inputMin: Float,
    inputMax: Float,
    outputMin: Float,
    outputMax: Float
): Float {
    // Calculate the normalized position of the value within the input range (0 to 1)
    val normalizedValue = (this - inputMin) / (inputMax - inputMin)

    // Map the normalized value to the output range
    return Math.clamp(outputMin + normalizedValue * (outputMax - outputMin), outputMin, outputMax)
}

fun drawLine(a: Location, b: Location, particle: Particle) {
    fun lerp(a: Double, b: Double, t: Double) = (1 - t) * a + t * b

    val numPoints = a.distance(b).toInt() * 2
    for (i in 0..numPoints) {
        val t = i.toDouble() / numPoints
        val pos = Location(
            a.world,
            lerp(a.x, b.x, t),
            lerp(a.y, b.y, t),
            lerp(a.z, b.z, t),
        )
        a.world.spawnParticle(particle, pos, 1, 0.0, 0.0, 0.0, 0.0)
    }

}

fun Player.consumePartyItem(time: Long = 20 * 20) {
    this.showTitle(Title.title(Component.empty(), Component.text("Consuming this party item in ${time / 20} seconds...")))

    val item = inventory.itemInMainHand
    item.mapTimer(MapKey.PARTY_ITEM_COOLDOWN, { inventory.removeItem(item) }, time, false)
}


// ik im not supposed to be using this but idc its nice
inline fun <reified T> Metadatable.getMetadata(key: String) = (this.getMetadata(key).firstOrNull { it.owningPlugin == PLUGIN && it.value() is T })?.value() as? T
fun <T> Metadatable.setMetadata(key: String, value: T) = this.setMetadata(key, FixedMetadataValue(PLUGIN, value))


enum class MapKey { PARTY_ITEM_COOLDOWN }

private val mappedThings = mutableMapOf<Pair<Any, MapKey>, Any>()

fun Any.map(key: MapKey, value: Any, replace: Boolean) {
    val pair = Pair(this, key)
    if (!replace && pair in mappedThings) return;
    mappedThings[pair] = value
}

fun Any.mapTimer(key: MapKey, task: () -> Unit, time: Long, replace: Boolean) {
    val pair = Pair(this, key)
    val existingTask = mappedThings[pair]
    if (!replace && existingTask != null) return
    if (existingTask is BukkitTask) existingTask.cancel()
    mappedThings[pair] = Bukkit.getScheduler().runTaskLater(PLUGIN, Runnable {
        mappedThings.remove(pair)
        task()
    }, time)
}

fun Any.unmap(key: MapKey) {
    val pair = Pair(this, key)
    val value = mappedThings.remove(pair)
    if (value is BukkitTask) value.cancel()
}

fun Any.isMapped(key: MapKey) = Pair(this, key) in mappedThings
fun Any.getMappedThing(key: MapKey) = mappedThings[Pair(this, key)]
