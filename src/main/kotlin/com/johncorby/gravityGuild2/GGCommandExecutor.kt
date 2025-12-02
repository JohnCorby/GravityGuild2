package com.johncorby.gravityGuild2

import org.battleplugins.arena.Arena
import org.battleplugins.arena.command.ArenaCommand
import org.battleplugins.arena.command.ArenaCommandExecutor
import org.bukkit.entity.Player

class GGCommandExecutor(arena: Arena) : ArenaCommandExecutor(arena) {
    @ArenaCommand(commands = ["reload", "r"], description = "Reload plugin for debugging", permissionNode = "reload")
    fun reload(player: Player) {
        player.performCommand("gg advance")
        player.performCommand("gg advance")
        player.performCommand("gg advance")
        player.performCommand("gg advance")

        player.performCommand("plm reload GravityGuild2")
        player.performCommand("ba reload")
    }
}