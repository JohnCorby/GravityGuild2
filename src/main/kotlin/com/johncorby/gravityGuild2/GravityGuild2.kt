package com.johncorby.gravityGuild2

import com.destroystokyo.paper.event.server.ServerTickStartEvent
import org.battleplugins.arena.BattleArena
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

lateinit var plugin: GravityGuild2

class GravityGuild2 : JavaPlugin(), Listener {
    override fun onEnable() {
        // Plugin startup logic
        plugin = this

        saveResource("arenas/gravityguild.yml", true)
        BattleArena.getInstance().registerArena(this, "GravityGuild", GGArena::class.java)

//        Bukkit.getPluginManager().registerEvents(this, this)
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }

    @EventHandler
    fun onTick(event: ServerTickStartEvent) {
        logger.info("tick")
    }
}
