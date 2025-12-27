package com.johncorby.gravityGuild2

import org.battleplugins.arena.Arena
import org.battleplugins.arena.command.ArenaCommand
import org.battleplugins.arena.command.ArenaCommandExecutor
import org.bukkit.entity.Player

class GGCommandExecutor(arena: Arena) : ArenaCommandExecutor(arena) {
    @ArenaCommand(commands = ["reload", "r"], description = "debug: reload plugin", permissionNode = "debug")
    fun reload(player: Player) {
        player.performCommand("plm reload GravityGuild2")
        player.performCommand("ba reload")
    }

    @ArenaCommand(commands = ["givePartyItem"], description = "debug: run give party item function", permissionNode = "debug")
    fun givePartyItem(player: Player) = player.givePartyItem()
}