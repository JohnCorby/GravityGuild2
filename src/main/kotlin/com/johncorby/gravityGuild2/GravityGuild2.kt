package com.johncorby.gravityGuild2

import org.battleplugins.arena.BattleArena
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

lateinit var PLUGIN: GravityGuild2

class GravityGuild2 : JavaPlugin(), Listener {
    override fun onEnable() {
        // Plugin startup logic
        PLUGIN = this

        // write our arena config here so battle arena recognizes our game
        saveResource("arenas/gravityguild.yml", true)
        BattleArena.getInstance().registerArena(this, "GravityGuild", GGArena::class.java)
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
