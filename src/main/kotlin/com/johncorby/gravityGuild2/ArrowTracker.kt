package com.johncorby.gravityGuild2

import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Arrow
import org.bukkit.entity.Player
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

                val closestPlayer = arrow.world.getNearbyEntities(
                    arrow.location,
                    3.0, 3.0, 3.0,
                    { it is Player && it != arrow && it != arrow.shooter && it.isGliding },
                ).minByOrNull { it.location.distance(arrow.location) } as Player?
                if (closestPlayer != null) {
                    // was way too op for mace, now its way too op for arrows LOL
//                    arrow.teleport(closestEntity)

                    closestPlayer.isGliding = false
                    closestPlayer.world.playSound(closestPlayer, Sound.ENTITY_PLAYER_BURP, 1f, 1f)
                    (arrow.shooter as Player).attack(closestPlayer)
                    // TODO: make it prevent u from re-elytra-ing for n seconds. could water bucket land to prevent damage?

//                    arrow.hitEntity(closestPlayer)


                    dontGlide.add(closestPlayer)
                    Bukkit.getScheduler().runTaskLater(plugin, Runnable { dontGlide.remove(closestPlayer) }, 10)
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
