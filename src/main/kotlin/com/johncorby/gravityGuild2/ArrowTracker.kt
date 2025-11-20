package com.johncorby.gravityGuild2

import org.bukkit.Bukkit
import org.bukkit.entity.Arrow
import org.bukkit.util.Vector


object ArrowTracker {
    // TODO: broken with multiple competitions :P
    private val tracked = mutableMapOf<Arrow, Vector>()

    fun Arrow.startTracking() = tracked.put(this, velocity)
    fun Arrow.stopTracking() = tracked.remove(this)
    fun stopTracking() = tracked.clear()

    init {
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            for ((arrow, velocity) in tracked)
                arrow.velocity = velocity
        }, 0, 20)
    }
}
