package com.johncorby.gravityGuild2

import org.battleplugins.arena.Arena
import org.battleplugins.arena.event.ArenaEventHandler
import org.battleplugins.arena.event.player.ArenaJoinEvent

class GGArena : Arena() {
    @ArenaEventHandler
    fun onJoin(event: ArenaJoinEvent) {
        plugin.logger.info("${event.arenaPlayer.describe()} joined ${event.arena.describe()}")
    }
}