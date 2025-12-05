package com.johncorby.gravityGuild2

import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Arrow
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector


// this has no reason to be in a separate file but it is
object ArrowTracker {
    // broken with multiple competitions? except its not, it works itself out i think...
    private val tracked = mutableMapOf<Arrow, Vector>()

    fun Arrow.startTracking() = tracked.put(this, velocity)
    fun Arrow.stopTracking() = tracked.remove(this)
    fun stopTracking() = tracked.clear()

    init {
        Bukkit.getScheduler().runTaskTimer(PLUGIN, Runnable {
            for ((arrow, velocity) in tracked) {
                arrow.velocity = velocity // arrow slows down, retain velocity

                val nearbyEntities = arrow.world.getNearbyEntities(
                    arrow.location,
                    3.0, 3.0, 3.0,
                    { it is Player && it != arrow && it != arrow.shooter && (it.isGliding || it.isMarkedForDeath) },
                )
                nearbyEntities.forEach {
                    val nearbyPlayer = it as Player

                    // was way too op for mace, now its way too op for arrows LOL
//                    arrow.teleport(closestEntity)

                    if (it.isMarkedForDeath) {
                        arrow.hitEntity(nearbyPlayer) // bye bye
                        return@forEach
                    }


                    nearbyPlayer.isGliding = false
                    nearbyPlayer.world.playSound(nearbyPlayer, Sound.ENTITY_PLAYER_BURP, 1f, 1f)
                    (arrow.shooter as Player).attack(nearbyPlayer)



                    dontGlide.add(nearbyPlayer)
                    Bukkit.getScheduler().runTaskLater(PLUGIN, Runnable { dontGlide.remove(nearbyPlayer) }, 20)
                }
            }
        }, 0, 0)
    }
}


var Player.isMarkedForDeath: Boolean
    get() = this.hasPotionEffect(PotionEffectType.GLOWING)
    set(value) {
        if (value) this.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, 10 * 20, 1, false, false))
        else this.removePotionEffect(PotionEffectType.GLOWING)
    }

var Player.isCooldown: Boolean
    get() = hasPotionEffect(PotionEffectType.INVISIBILITY) && hasPotionEffect(PotionEffectType.NIGHT_VISION)
    set(value) {
        if (value) {
            // BUG: doesnt hide clothes
            addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, 20 * 3, 1, false, false))
            // just to get ur surroundings
            addPotionEffect(PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 3, 1, false, false))
        } else {
            removePotionEffect(PotionEffectType.INVISIBILITY)
            removePotionEffect(PotionEffectType.NIGHT_VISION)
        }
    }



// why is this here? who cares!
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
