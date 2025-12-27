package com.johncorby.gravityGuild2

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.battleplugins.arena.BattleArena
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path

lateinit var PLUGIN: GravityGuild2

class GravityGuild2 : JavaPlugin(), Listener {
    override fun onEnable() {
        // Plugin startup logic
        PLUGIN = this

        // write our arena config here so battle arena recognizes our game
        saveResource("arenas/gravityguild.yml", true)
        saveResource("scoreboards.yml", true)
        Files.move(Path(dataFolder.path, "scoreboards.yml"), Path(dataFolder.parent, "BattleArena", "scoreboards.yml"), StandardCopyOption.REPLACE_EXISTING)
        BattleArena.getInstance().registerArena(this, "GravityGuild", GGArena::class.java)

        Bukkit.getPluginManager().registerEvents(this, this)
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }

    @EventHandler
    fun PlayerJoinEvent.handler() {
//        if (player.lastLogin != 0L) return

        // silly hack
        player.performCommand("mvtp gg_arenas")
        player.gameMode = GameMode.ADVENTURE
        player.sendMessage(
            Component.text("Welcome to GravityGuild").color(NamedTextColor.GOLD)
                .appendNewline()
                .append(Component.text("Created by JohnCorby and FunkyBoots111").color(NamedTextColor.YELLOW))
                .appendNewline()
                .append(Component.text("Maps by FunkyBoots111 and WonkyPanda").color(NamedTextColor.YELLOW))
                .appendNewline()
                .append(Component.text("Design by JohnCorby, FunkyBoots111, WonkyPanda, and PowerUser64").color(NamedTextColor.YELLOW))
                .appendNewline()
                .append(Component.text("Type /gg_arenas or click this text to see maps").color(NamedTextColor.WHITE).clickEvent(ClickEvent.runCommand("/gg_arenas")))
                .appendNewline()
                .append(Component.text("Type /gg to see list of all the other commands").color(NamedTextColor.WHITE))
        )
    }
}
