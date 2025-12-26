package com.johncorby.gravityGuild2

import io.papermc.paper.event.player.PlayerFailMoveEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.battleplugins.arena.Arena
import org.battleplugins.arena.ArenaPlayer
import org.battleplugins.arena.competition.LiveCompetition
import org.battleplugins.arena.competition.PlayerRole
import org.battleplugins.arena.competition.map.LiveCompetitionMap
import org.battleplugins.arena.competition.phase.CompetitionPhaseType
import org.battleplugins.arena.competition.phase.phases.VictoryPhase
import org.battleplugins.arena.event.ArenaEventHandler
import org.battleplugins.arena.event.arena.ArenaPhaseCompleteEvent
import org.battleplugins.arena.event.arena.ArenaPhaseStartEvent
import org.battleplugins.arena.event.player.ArenaJoinEvent
import org.battleplugins.arena.event.player.ArenaLeaveEvent
import org.battleplugins.arena.stat.ArenaStats
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.damage.DamageType
import org.bukkit.entity.*
import org.bukkit.event.entity.*
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.player.*
import org.bukkit.inventory.EquipmentSlot

class GGArena : Arena() {
    override fun createCommandExecutor() = GGCommandExecutor(this)

    init {
        eventManager.registerArenaResolver(ProjectileLaunchEvent::class.java) { event ->
            ArenaPlayer.getArenaPlayer(event.entity.shooter as? Player)?.competition
        }
        eventManager.registerArenaResolver(ProjectileHitEvent::class.java) { event ->
            ArenaPlayer.getArenaPlayer(event.entity.shooter as? Player)?.competition
        }
    }

    /*
        @ArenaEventHandler
        fun EntityKnockbackEvent.handler() {
            if (cause == EntityKnockbackEvent.Cause.EXPLOSION) {
                // stop wind charge (and everything else)
                isCancelled = true
            }
        }
    */

    @ArenaEventHandler
    fun ProjectileLaunchEvent.handler() {
//        PLUGIN.logger.info("${entity.shooter} shoot ${entity}\nshooter vel = ${(entity.shooter as Player).velocityZeroGround}")

        when (entity) {
            is Arrow -> GGBow.launch(entity as Arrow)
            is Snowball -> GGSnowball.launch(entity as Snowball)
        }
    }

    @ArenaEventHandler
    fun ProjectileHitEvent.handler(competition: LiveCompetition<*>) {
        when (entity) {
            is Arrow -> GGBow.hit(entity as Arrow)
            is Snowball -> GGSnowball.hit(entity as Snowball, hitEntity)
            is WindCharge -> GGMace.hit(entity as WindCharge, competition)
            is EnderPearl -> isCancelled = GGTnt.hit(entity as EnderPearl)
        }

    }

    @ArenaEventHandler
    fun PlayerInteractEvent.handler(competition: LiveCompetition<*>) {
        if (hand != EquipmentSlot.HAND) return

        // off hand exists, i dont care
        when (player.inventory.itemInMainHand) {
            Items.MACE.item -> {
                if (action.isLeftClick)
                    GGMace.launch(player)
                else if (action.isRightClick)
                    GGMace.smash(player)
            }

            Items.TNT.item -> {
                if (action.isLeftClick || action.isRightClick)
                    GGTnt.launch(player, action.isRightClick)
            }

            Items.FISH.item -> {
                if (action.isLeftClick)
                    GGFish.attack(player)
                else if (action.isRightClick)
                    GGFish.puffer(player)
            }

            Items.ARROW.item -> {
                if (action.isLeftClick)
                    GGArrow.attack(player)
                else if (action.isRightClick)
                    GGArrow.launch(player)

            }

            Items.HORN.item -> {
                if (action.isRightClick)
                    GGHorn.use(player, competition)
            }

            Items.GUN.item -> {
                if (action.isLeftClick)
                    GGGun.attack(null, player)
            }

            Items.TREE.item -> {
                if (action.isLeftClick) GGTree.plant(player)
            }
        }
    }

    val playerLastSlot = mutableMapOf<Player, Int>()

    @ArenaEventHandler
    fun PlayerSwapHandItemsEvent.handler() {
        isCancelled = true

        val oldSlot = player.inventory.heldItemSlot
        player.inventory.heldItemSlot = playerLastSlot[player] ?: return
        heldItemChanged(player, oldSlot, player.inventory.heldItemSlot)
    }

    @ArenaEventHandler
    fun PlayerItemHeldEvent.handler() {
        heldItemChanged(player, previousSlot, newSlot)
    }

    // hack cuz event doesnt trigger it all the time
    fun heldItemChanged(player: Player, oldSlot: Int, newSlot: Int) {
        playerLastSlot[player] = oldSlot

        when (player.inventory.getItem(newSlot)) {
            Items.GUN.item -> {
                player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE)!!.apply { baseValue = 9999.0 }
                player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE)!!.apply { baseValue = 9999.0 }
            }
            // custom left/right click is weird when hitting entity or block, so just make that impossible
            Items.ARROW.item, Items.FISH.item, Items.TNT.item, Items.TREE.item -> {
                player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE)!!.apply { baseValue = 0.0 }
                player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE)!!.apply { baseValue = 0.0 }
            }

            else -> {
                player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE)!!.apply { baseValue = defaultValue }
                player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE)!!.apply { baseValue = defaultValue }
            }
        }

//        PLUGIN.logger.info("held item = ${this.player.inventory.getItem(this.newSlot)}")
    }

    @ArenaEventHandler
    fun PlayerFailMoveEvent.handler() {
        if (failReason == PlayerFailMoveEvent.FailReason.MOVED_TOO_QUICKLY) {
            // allow moved too quickly so slap works
            isAllowed = true
            PLUGIN.logger.warning("ALLOW fail move ${this.failReason}")
        }
    }

    // food level change prevented in config. could change this? its nice to not deal with hunger

    @ArenaEventHandler
    fun ArenaPhaseStartEvent.handler() {
        when (phase.type) {
            CompetitionPhaseType.INGAME -> {
                // arena restore puts back entities, so lets remove current ones
                val map = competition.map as LiveCompetitionMap
                val bounds = map.bounds!!
                for (entity in map.world.entities) {
                    if (bounds.isInside(entity.boundingBox) && entity !is Player) {
                        entity.remove()
                    }
                }
                // arena restore happens after this call. it lags a bit on the main thread but its fine i guess


                (competition as LiveCompetition).players.forEach { player ->
                    player.player.initInventory()
                    heldItemChanged(player.player, player.player.inventory.heldItemSlot, player.player.inventory.heldItemSlot)

                    player.player.isRespawning = true

                    // scoreboard module adds us to its own non global scoreboard. I THINK we need to add the team to THAT one to get the nametag thing working
                    // this gets removed on remove-scoerboard so hopefully thatll remove the team effects
//                    val team = player.player.scoreboard.getTeam("GravityGuild") ?: player.player.scoreboard.registerNewTeam("GravityGuild")
//                    team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER)
//                    team.addPlayer(player.player)
                }
            }

            CompetitionPhaseType.VICTORY -> {
                // reload triggers this
                if ((competition as LiveCompetition).players.isEmpty()) return

                Bukkit.broadcast(Component.text("Game ${competition.map.name} ended with stats:").color(NamedTextColor.YELLOW))
                (competition as LiveCompetition).players.sortedByDescending { it.stat(ArenaStats.KILLS).get() }.forEach {
                    Bukkit.broadcast(Component.text("${it.player.name}: ${it.stat(ArenaStats.KILLS).get()} kills"))
                }
            }
        }
    }

    @ArenaEventHandler
    fun ArenaPhaseCompleteEvent.handler() {
        when (phase.type) {
            CompetitionPhaseType.INGAME -> {

                (competition as LiveCompetition).players.forEach { player ->
//                    val team = player.player.scoreboard.getTeam("GravityGuild")!!
//                    team.removePlayer(player.player)
                }
            }

            CompetitionPhaseType.VICTORY -> {
                // reload triggers this
            }
        }
    }

    @ArenaEventHandler
    fun ArenaJoinEvent.handler() {
        if (this.competition.players.size == 1) {
            Bukkit.broadcast(
                Component.text("Arena ${this.competition.map.name} has someone in it! Click to join")
                    .color(NamedTextColor.GOLD)
                    .clickEvent(ClickEvent.runCommand("/gg join ${this.competition.map.name}"))
            )
        }

        player.saturation = 9999f
        player.saturatedRegenRate = 20
        player.unsaturatedRegenRate = 20

        GGMace.trackedPlayers.add(player)

        // if everyone is in, just start the game
        if (this.competition.players.size == Bukkit.getOnlinePlayers().size)
            Bukkit.getScheduler().runTaskLater(PLUGIN, Runnable {
                this.competition.phaseManager.setPhase(CompetitionPhaseType.INGAME, true)
            }, 10);
    }

    @ArenaEventHandler
    fun ArenaLeaveEvent.handler() {
//        val team = Bukkit.getScoreboardManager().mainScoreboard.getTeam("GravityGuild")!!
//        team.removePlayer(player)

        GGMace.trackedPlayers.remove(player)
        GGTree.playerLastPlanted.remove(player)

        // remove all projectiles associated with player in the dumbest way possible. TODO: make this better
        player.world.entities.filter { it is Projectile && it.shooter == player }.forEach {
            it.remove()
            it.passengers.forEach { it.remove() }
        }

        playerLastSlot.remove(player)
        playerLastDamager.remove(player)
        playerPendingKills.removeAll { (killer, event) -> killer == player || event.player == player }

        // because our victory condition is only time limit, it doesnt close early. we gotta do this ourselves
        if (this.arenaPlayer.role == PlayerRole.PLAYING && this.competition.players.size <= 1) {
            this.competition.phaseManager.setPhase(CompetitionPhaseType.VICTORY, true)
            // actually trigger the victory for that player :P
            (this.competition.phaseManager.currentPhase as VictoryPhase).onVictory(this.competition.players.toSet())
            this.competition.victoryManager.end(false)
        }
    }

    val playerLastDamager = mutableMapOf<Player, Pair<Player, Int>>()
    val playerPendingKills = mutableListOf<Pair<Player, PlayerDeathEvent>>()

    @ArenaEventHandler
    fun EntityDamageEvent.handler(competition: LiveCompetition<*>) {
        if (damageSource.causingEntity is Player &&
            (damageSource.causingEntity as Player).isRespawning
        ) {
            // respawning players can do no damage
            isCancelled = true
            return
        }

        if (damageSource.causingEntity is Player &&
            (damageSource.causingEntity as Player).inventory.itemInMainHand == Items.GUN.item &&
            damageSource.damageType == DamageType.PLAYER_ATTACK
        ) {
            isCancelled = true
            GGGun.attack(entity, damageSource.causingEntity as Player)
            return
        }

        if (damageSource.causingEntity is Player &&
            damageSource.directEntity is WitherSkull
        ) {
            isCancelled = true
            GGArrow.hit(entity, damageSource.directEntity as WitherSkull)
            return
        }

        if (this.entity !is Player) return // the rest should only apply to players

        PLUGIN.logger.info("${entity.name} lost $damage health from $cause")

        if ((entity as Player).isRespawning) {
            isCancelled = true
            return
        }

        // revert non lethal damage only for hit ground and wall. everything else should be normal damage
        if (cause == DamageCause.FALL || cause == DamageCause.FLY_INTO_WALL) {
            // revert non-lethal damage
            if ((entity as Player).health - damage > 0) damage = 0.0
        }

        if (cause == DamageCause.VOID) damage = 9999.0 // just fuckin kill em

        val damagingPlayer = this.damageSource.causingEntity as? Player
        if (damagingPlayer != null && damagingPlayer != entity) {
            // damaged by other player. track this
            // overwrite previous value in case of multiple damages
            playerLastDamager[entity as Player] = damagingPlayer to Bukkit.getCurrentTick()
            PLUGIN.logger.info("tracking ${entity.name} got damaged by ${damagingPlayer.name} at ${Bukkit.getCurrentTick()}")

            // tf2 moment teehee
            damagingPlayer.playSound(damagingPlayer, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
        }
    }

    @ArenaEventHandler
    fun PlayerDeathEvent.handler(competition: LiveCompetition<*>) {
        isCancelled = true // dont kill and dont call ArenaDeathEvent. we do our own thing

//        when (competition.phase) {
//            CompetitionPhaseType.INGAME -> {
//                            player.initAndSpawn()
//            }
//        }

        // death stuff
        GGBow.dontGlide.remove(player)

        Bukkit.broadcast(this.deathMessage()!!)

        if (damageSource.damageType == DamageType.FLY_INTO_WALL || damageSource.damageType == DamageType.FALL) {
            player.world.createExplosion(player.location, 5f) // for literally no reason
        }

        // okay, now use our custom killer thing to track kills
        playerLastDamager[player]?.let { (lastDamager, lastDamageTick) ->
            PLUGIN.logger.info("${player.name} last damaged by ${lastDamager.name} at $lastDamageTick (now is ${Bukkit.getCurrentTick()})")

            // did it recently enough, give em the kill
            if (Bukkit.getCurrentTick() - lastDamageTick < 20 * 5) {
                playerPendingKills.add(lastDamager to this)
                // may be cancelled below next tick, so wait 2
                Bukkit.getScheduler().runTaskLater(PLUGIN, Runnable {
                    if (playerPendingKills.removeAll { (killer, event) -> event == this }) {
                        ArenaPlayer.getArenaPlayer(lastDamager)?.computeStat(ArenaStats.KILLS) { old -> (old ?: 0) + 1 }
                        Bukkit.broadcast(Component.text("KILL: ${lastDamager.name} -> ${player.name}").color(NamedTextColor.YELLOW))


                        // tf2 moment teehee
                        lastDamager.playSound(lastDamager, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
                    }
                }, 2)
            }

        }
        playerLastDamager.remove(player) // stop tracking who last hurt them because they are dead
//        playerLastDamager.getOrPut(player) { mutableMapOf() }.clear()

        Bukkit.getScheduler().runTask(PLUGIN, Runnable {
            // if killer dies at the same tick as killed, no matter the order, killer pending kills will be cancelled
            playerPendingKills.removeAll { (killer, event) -> killer == player }
        })


        Bukkit.getScheduler().runTask(PLUGIN, Runnable {
            player.isRespawning = true
        })
    }

    @ArenaEventHandler
    fun PlayerDropItemEvent.handler() {
        // dont let players drop our items
        if (Items.entries.any { it.item == itemDrop.itemStack })
            isCancelled = true
    }

    @ArenaEventHandler
    fun PlayerItemDamageEvent.handler() {
        if (Items.entries.any { it.item == this.item })
            isCancelled = true
    }

    /*
        @ArenaEventHandler
        fun BlockPlaceEvent.handler() {
            if (itemInHand == Items.TNT.item || itemInHand == Items.TREE.item) {
                isCancelled = true
            }
        }
    */

    @ArenaEventHandler
    fun EntityToggleGlideEvent.handler() {
        if (isGliding && entity in GGBow.dontGlide) {
            val player = entity as Player
            player.world.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, .5f)
            isCancelled = true
        }
//        (entity as Attributable).getAttribute(Attribute.SCALE)!!.apply {
//            baseValue = if (isGliding) 3.0 else defaultValue
//        }
    }
}