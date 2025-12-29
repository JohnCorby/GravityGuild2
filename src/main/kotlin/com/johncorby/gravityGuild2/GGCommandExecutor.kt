package com.johncorby.gravityGuild2

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import net.kyori.adventure.text.event.ClickEvent
import org.battleplugins.arena.Arena
import org.battleplugins.arena.command.ArenaCommand
import org.battleplugins.arena.command.ArenaCommandExecutor
import org.battleplugins.arena.competition.map.CompetitionMap
import org.battleplugins.arena.competition.map.LiveCompetitionMap
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.util.Vector

class GGCommandExecutor(arena: Arena) : ArenaCommandExecutor(arena) {
    @ArenaCommand(commands = ["reload", "r"], description = "debug: reload plugin", permissionNode = "debug")
    fun reload(player: Player) {
        player.performCommand("plm reload GravityGuild2")
        player.performCommand("ba reload")
    }

    @ArenaCommand(commands = ["givePartyItem"], description = "debug: run give party item function", permissionNode = "debug")
    fun givePartyItem(player: Player) = player.givePartyItem()

    @ArenaCommand(commands = ["verifyBounds"], description = "debug: print out bounds info and let you tp to them", permissionNode = "debug")
    fun verifyBounds(player: Player, map: CompetitionMap) {
        val bb = (map as LiveCompetitionMap).bounds!!.toBoundingBox()
        val options = ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build()
        player.sendMessage(
            Component.text("${map.name} bounds:")
                .appendNewline()
                .append(Component.text("dimensions: ${bb.max.subtract(bb.min)}"))
                .appendNewline()
                .append(Component.text("center: ${bb.center} (click to tp)").clickEvent(ClickEvent.callback({ player.teleport(bb.center.toLocation(map.world, player.yaw, player.pitch)) }, options)))
                .appendNewline()
                .append(Component.text("min: ${bb.min} (click to tp)").clickEvent(ClickEvent.callback({ player.teleport(bb.min.toLocation(map.world, player.yaw, player.pitch)) }, options)))
                .appendNewline()
                .append(Component.text("max: ${bb.max} (click to tp)").clickEvent(ClickEvent.callback({ player.teleport(bb.max.toLocation(map.world, player.yaw, player.pitch)) }, options)))
        )
    }
}