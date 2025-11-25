package com.johncorby.gravityGuild2

import org.bukkit.Bukkit
import org.bukkit.entity.Arrow
import org.bukkit.entity.Damageable
import org.bukkit.util.Vector


object ArrowTracker {
    // TODO: broken with multiple competitions :P
    private val tracked = mutableMapOf<Arrow, Vector>()

    fun Arrow.startTracking() = tracked.put(this, velocity)
    fun Arrow.stopTracking() = tracked.remove(this)
    fun stopTracking() = tracked.clear()

    init {
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            for ((arrow, velocity) in tracked) {
                arrow.velocity = velocity

                val closestEntity = arrow.world.getNearbyEntities(arrow.location, 3.0, 3.0, 3.0)
                    .filter { it is Damageable && it != arrow && it != arrow.shooter }
                    .minBy { it.location.distance(arrow.location) }
                if (closestEntity != null) {
                    // was way too op for mace, now its way too op for arrows LOL
                    arrow.teleport(closestEntity)
                    arrow.hitEntity(closestEntity)

                }
            }
        }, 0, 0)
    }
}


fun Double.remap(
    inputMin: Double,
    inputMax: Double,
    outputMin: Double,
    outputMax: Double
): Double {
    // Calculate the normalized position of the value within the input range (0 to 1)
    val normalizedValue = (this - inputMin) / (inputMax - inputMin)

    // Map the normalized value to the output range
    return outputMin + normalizedValue * (outputMax - outputMin)
}
