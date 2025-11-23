package com.johncorby.gravityGuild2

import org.battleplugins.arena.BattleArena
import org.bukkit.Bukkit
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scoreboard.Team

lateinit var plugin: GravityGuild2

class GravityGuild2 : JavaPlugin(), Listener {
    override fun onEnable() {
        // Plugin startup logic
        plugin = this

        // write our arena config here so battle arena recognizes our game
        saveResource("arenas/gravityguild.yml", true)
        BattleArena.getInstance().registerArena(this, "GravityGuild", GGArena::class.java)

        val team = Bukkit.getScoreboardManager().mainScoreboard.registerNewTeam("GravityGuild")
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER)
    }

    override fun onDisable() {
        // Plugin shutdown logic

        Bukkit.getScoreboardManager().mainScoreboard.getTeam("GravityGuild")?.unregister()
    }
}
