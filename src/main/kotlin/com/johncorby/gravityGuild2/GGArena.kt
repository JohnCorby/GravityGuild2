package com.johncorby.gravityGuild2

import io.papermc.paper.event.entity.EntityKnockbackEvent
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
import org.bukkit.damage.DamageSource
import org.bukkit.damage.DamageType
import org.bukkit.entity.*
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.*
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemDamageEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import kotlin.random.Random

// this file is probably way too big... too bad!
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

    @ArenaEventHandler
    fun EntityKnockbackEvent.handler() {
        if (cause == EntityKnockbackEvent.Cause.EXPLOSION) {
            // stop wind charge (and everything else)
            isCancelled = true
        }
    }

    @ArenaEventHandler
    fun ProjectileLaunchEvent.handler() {
//        PLUGIN.logger.info("${entity.shooter} shoot ${entity}\nshooter vel = ${(entity.shooter as Player).velocityZeroGround}")

        // dont include player velocity in projectile velocity
        entity.velocity = entity.velocity.subtract((entity.shooter as Player).velocityZeroGround)

        when (entity) {
            is Arrow -> GGBow.launch(entity)
            is Snowball -> GGSnowball.launch(entity)
        }
    }

    @ArenaEventHandler
    fun ProjectileHitEvent.handler(competition: LiveCompetition<*>) {
        when (entity) {
            is Arrow -> GGBow.hit(entity)
            is Snowball -> GGSnowball.hit(entity, hitEntity)
            is WindCharge -> Mace.hit(entity, competition)
            is EnderPearl -> {
                isCancelled = true // dont teleport
                Tnt.hit(entity)
            }
        }

    }

    @ArenaEventHandler
    fun PlayerInteractEvent.handler(competition: LiveCompetition<*>) {
        // off hand exists, i dont care
        when (player.inventory.itemInMainHand) {
            Items.MACE.item -> {
                if (action.isRightClick)
                    Mace.launch(player)
                else if (action.isLeftClick)
                    Mace.smash(player)
            }

            Items.TNT.item -> {
                if (action == Action.PHYSICAL) return
                Tnt.launch(player, action.isRightClick)
            }

            Items.FISH.item -> {
                if (!action.isLeftClick) return
                Fish.attack(player)
            }

            Items.ARROW.item -> {
                if (action.isLeftClick)
                    Knife.attack(player)
                else if (action.isRightClick)
                    Knife.launch(player)

            }

            Items.HORN.item -> {
                if (!action.isRightClick)
                    Horn.use(player, competition)
            }
        }
    }

    @ArenaEventHandler
    fun PlayerItemHeldEvent.handler() {
        when (player.inventory.getItem(newSlot)) {
            Items.GUN.item -> {
                player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE)!!.apply { baseValue = 9999.0 }
                player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE)!!.apply { baseValue = 9999.0 }
            }
            // custom left/right click is weird when hitting entity or block, so just make that impossible
            Items.MACE.item, Items.ARROW.item, Items.FISH.item, Items.TNT.item -> {
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

    @ArenaEventHandler
    fun EntityDamageEvent.handler(competition: LiveCompetition<*>) {
        if (cause == DamageCause.FIRE_TICK) {
//            PLUGIN.logger.info("fire tick on ${entity.name}")

//            val unitRandom = Vector.getRandom().multiply(2).subtract(Vector(1, 1, 1))
//            entity.velocity = unitRandom.multiply(.5)
        }

        // TODO: fix gun logic
        if (cause == DamageCause.ENTITY_ATTACK &&
            damageSource.causingEntity is Player &&
            (damageSource.causingEntity as Player).inventory.itemInMainHand == Items.GUN.item
        ) {
            damage = 9999.0
        }

        if (damageSource.directEntity is WitherSkull) {
            // wither skulls do NOTHING
            // BUG: 2 kill messages
            isCancelled = true
            // just kidding they do a little bit
            (entity as Damageable).damage(3.0, damageSource.causingEntity, DamageType.WITHER_SKULL)
            return
        }

        if (this.entity !is Player) return // the rest should only apply to players

        PLUGIN.logger.info("${entity.name} lost $damage health from $cause")

        if ((entity as Player).isCooldown) {
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
            playerLastDamager[entity as Player] = damagingPlayer to Bukkit.getCurrentTick()
            PLUGIN.logger.info("tracking ${entity.name} got damaged by ${damagingPlayer.name} at ${Bukkit.getCurrentTick()}")

            // tf2 moment teehee
            damagingPlayer.playSound(damagingPlayer, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
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

                    // scoreboard module adds us to its own non global scoreboard. I THINK we need to add the team to THAT one to get the nametag thing working
                    // this gets removed on remove-scoerboard so hopefully thatll remove the team effects
//                    val team = player.player.scoreboard.getTeam("GravityGuild") ?: player.player.scoreboard.registerNewTeam("GravityGuild")
//                    team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER)
//                    team.addPlayer(player.player)
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
                // reload triggers this so nothing should happen here for speed lol
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

        Mace.trackedPlayers.add(player)

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

        Mace.trackedPlayers.remove(player)

        playerLastDamager.remove(player)

        // because our victory condition is only time limit, it doesnt close early. we gotta do this ourselves
        if (this.arenaPlayer.role == PlayerRole.PLAYING && this.competition.players.size <= 1) {
            this.competition.phaseManager.setPhase(CompetitionPhaseType.VICTORY, true)
            // actually trigger the victory for that player :P
            (this.competition.phaseManager.currentPhase as VictoryPhase).onVictory(this.competition.players.toSet())
        }
    }

    //    data class PlayerDamageData(var lastDamagedTick: Int, var totalDamage: Double)

    val playerLastDamager = mutableMapOf<Player, Pair<Player, Int>>()

    @ArenaEventHandler
    fun PlayerDeathEvent.handler(competition: LiveCompetition<*>) {
        isCancelled = true // dont kill and dont call ArenaDeathEvent. we do our own thing

//        when (competition.phase) {
//            CompetitionPhaseType.INGAME -> {
//                            player.initAndSpawn()
//            }
//        }

        // death stuff
        dontGlide.remove(player)

        Bukkit.broadcast(this.deathMessage()!!)

        // okay, now use our custom killer thing to track kills
        // TODO: maybe move this to PlayerDeathEvent if we wait before respawning
        // TODO: check this after a second to make sure the killer didnt die themselves
        playerLastDamager[player]?.let { lastDamager ->
            PLUGIN.logger.info("${player.name} last damaged by ${lastDamager.first.name} at ${lastDamager.second} (now is ${Bukkit.getCurrentTick()})")

            if (Bukkit.getCurrentTick() - lastDamager.second < 20 * 5) { // did it recently enough, give em the kill
                ArenaPlayer.getArenaPlayer(lastDamager.first)?.computeStat(ArenaStats.KILLS) { old -> (old ?: 0) + 1 }
                Bukkit.broadcast(Component.text("Kill credit goes to ${lastDamager.first.name}").color(NamedTextColor.GRAY))


                // tf2 moment teehee
                lastDamager.first.playSound(lastDamager.first, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)

                // v1 behavior: teleport killer to killed
//                player.teleport(lastDamager.first)
            }
        }

        playerLastDamager.remove(player) // stop tracking who last hurt them becasue they are dead
//        playerLastDamager.getOrPut(player) { mutableMapOf() }.clear()


        Bukkit.getScheduler().runTask(PLUGIN, Runnable {
            // respawn
            val spawns = competition.map.spawns!!.teamSpawns!!["Default"]!!.spawns!!
            val spawn = spawns[Random.nextInt(spawns.size)]
            PLUGIN.logger.info("respawn at ${spawn.toLocation(competition.map.world)}")
            player.teleport(spawn.toLocation(competition.map.world))


            // post respawn effects
            player.saturation = 9999f
            player.saturatedRegenRate = 20
            player.unsaturatedRegenRate = 20

            // these persist but after death but not visually, so this makes the visual appear
//            Items.entries.forEach { player.setCooldown(it.item, player.getCooldown(it.item)) }

            // this does cooldown
            player.isCooldown = true
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

    @ArenaEventHandler
    fun BlockPlaceEvent.handler() {
        if (itemInHand == Items.TNT.item) {
            isCancelled = true
        }
    }

    @ArenaEventHandler
    fun EntityToggleGlideEvent.handler() {
        if (isGliding && entity in dontGlide) {
            val player = entity as Player
            player.world.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, .5f)
            isCancelled = true
        }
//        (entity as Attributable).getAttribute(Attribute.SCALE)!!.apply {
//            baseValue = if (isGliding) 3.0 else defaultValue
//        }
    }
}

// dont feel like having custom competition logic so ill just track stuff globally and make sure to clear it out
// if you leave and join an arena really quickly youll get cleared out of this, but thats really unlikely
val dontGlide = mutableSetOf<Player>()

fun Damageable.damage(amount: Double, source: Entity?, damageType: DamageType) {
    var builder = DamageSource.builder(damageType).withDamageLocation(this.location)
    if (source != null) builder = builder.withDirectEntity(source).withCausingEntity(source)
    this.damage(amount, builder.build())
}