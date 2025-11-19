/*
package com.johncorby.gravityguild.arena

import com.johncorby.coreapi.CONSOLE
import com.johncorby.coreapi.debug
import com.johncorby.coreapi.schedule
import org.bukkit.entity.Arrow
import org.bukkit.util.Vector

*/
/**
 * a sorta hacky solution to prevent anti-gravity arrows from slowing to a stop
 *//*

object ArrowTracker {
    private val tracked = mutableMapOf<Arrow, Vector>()

    fun Arrow.startTracking() = tracked.put(this, velocity).also { CONSOLE.debug("start tracking $this") }
    fun Arrow.stopTracking() = tracked.remove(this).also { CONSOLE.debug("stop tracking $this") }
    fun stopTracking() = tracked.clear()

    init {
        schedule(period = 20) {
            for ((arrow, velocity) in tracked)
                arrow.velocity = velocity
        }
    }
}
*/
