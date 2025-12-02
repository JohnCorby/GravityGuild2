package com.johncorby.gravityGuild2

import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Arrow
import org.bukkit.entity.Player
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
                    { it is Player && it != arrow && it != arrow.shooter && it.isGliding },
                )
                nearbyEntities.forEach {
                    val nearbyPlayer = it as Player

                    // was way too op for mace, now its way too op for arrows LOL
//                    arrow.teleport(closestEntity)

                    nearbyPlayer.isGliding = false
                    nearbyPlayer.world.playSound(nearbyPlayer, Sound.ENTITY_PLAYER_BURP, 1f, 1f)
                    (arrow.shooter as Player).attack(nearbyPlayer)

//                    arrow.hitEntity(closestPlayer)


                    dontGlide.add(nearbyPlayer)
                    Bukkit.getScheduler().runTaskLater(PLUGIN, Runnable { dontGlide.remove(nearbyPlayer) }, 20)
                }
            }
        }, 0, 0)
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
