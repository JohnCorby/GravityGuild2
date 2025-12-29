package com.johncorby.gravityGuild2

import org.battleplugins.arena.Arena
import org.battleplugins.arena.command.ArenaCommand
import org.battleplugins.arena.command.ArenaCommandExecutor
import org.battleplugins.arena.competition.map.CompetitionMap
import org.battleplugins.arena.competition.map.LiveCompetitionMap
import org.bukkit.Location
import org.bukkit.entity.Player

class GGCommandExecutor(arena: Arena) : ArenaCommandExecutor(arena) {
    @ArenaCommand(commands = ["reload", "r"], description = "debug: reload plugin", permissionNode = "debug")
    fun reload(player: Player) {
        player.performCommand("plm reload GravityGuild2")
        player.performCommand("ba reload")
    }

    @ArenaCommand(commands = ["givePartyItem"], description = "debug: run give party item function", permissionNode = "debug")
    fun givePartyItem(player: Player) = player.givePartyItem()

    @ArenaCommand(commands = ["tpMin"], description = "debug: tp to min pos to verify bounds", permissionNode = "debug")
    fun tpMin(player: Player, map: CompetitionMap) {
        val bb = (map as LiveCompetitionMap).bounds!!.toBoundingBox()
        player.teleport(Location(map.world, bb.minX, bb.minY, bb.minZ))
        player.sendMessage("${map.name} min pos = ${bb.min}")
    }

    @ArenaCommand(commands = ["tpMax"], description = "debug: tp to max pos for verify bounds", permissionNode = "debug")
    fun tpMax(player: Player, map: CompetitionMap) {
        val bb = (map as LiveCompetitionMap).bounds!!.toBoundingBox()
        player.teleport(Location(map.world, bb.maxX, bb.maxY, bb.maxZ))
        player.sendMessage("${map.name} max pos = ${bb.max}")
    }
}