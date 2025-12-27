package com.johncorby.gravityGuild2

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
    }
}
