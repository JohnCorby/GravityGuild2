package com.johncorby.gravityGuild2

import org.battleplugins.arena.BattleArena
import org.bukkit.event.Listener
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
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
