package com.johncorby.gravityGuild2

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent
import com.johncorby.gravityGuild2.ArrowTracker.startTracking
import com.johncorby.gravityGuild2.ArrowTracker.stopTracking
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.event.player.PlayerFailMoveEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import net.kyori.adventure.util.TriState
import org.battleplugins.arena.Arena
import org.battleplugins.arena.ArenaPlayer
import org.battleplugins.arena.competition.LiveCompetition
import org.battleplugins.arena.competition.map.LiveCompetitionMap
import org.battleplugins.arena.competition.phase.CompetitionPhaseType
import org.battleplugins.arena.competition.phase.phases.VictoryPhase
import org.battleplugins.arena.event.ArenaEventHandler
import org.battleplugins.arena.event.arena.ArenaPhaseCompleteEvent
import org.battleplugins.arena.event.arena.ArenaPhaseStartEvent
import org.battleplugins.arena.event.player.ArenaJoinEvent
import org.battleplugins.arena.event.player.ArenaKillEvent
import org.battleplugins.arena.event.player.ArenaLeaveEvent
import org.battleplugins.arena.event.player.ArenaRespawnEvent
import org.battleplugins.arena.stat.ArenaStats
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.MusicInstrument
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.damage.DamageSource
import org.bukkit.damage.DamageType
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.*
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.entity.EntityToggleGlideEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemDamageEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f

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

        Bukkit.getScheduler().runTaskTimer(PLUGIN, Runnable {
            trackedMacePlayers.forEach {
                it.exp = it.velocity.length().toFloat().remapClamped(0f, 1f, 0f, 1f)
                it.level = ArenaPlayer.getArenaPlayer(it)!!.getStat<Int>(ArenaStats.KILLS)!!
            }
        }, 0, 0)
    }

    // use for movement cancel else it thinks ur falling slightly when ur not
    @Suppress("DEPRECATION")
    val Player.velocityZeroGround get() = if (isOnGround) Vector(0, 0, 0) else velocity

    @ArenaEventHandler
    fun ProjectileLaunchEvent.handler() {
//        PLUGIN.logger.info("${entity.shooter} shoot ${entity}\nshooter vel = ${(entity.shooter as Player).velocityZeroGround}")

        // dont include player velocity in projectile velocity
        entity.velocity = entity.velocity.subtract((entity.shooter as Player).velocityZeroGround)

        when (entity) {
            is Arrow -> {
                entity.setGravity(false)
                entity.visualFire = TriState.TRUE
//                val tnt = entity.world.spawn(entity.location, TNTPrimed::class.java)
//                entity.addPassenger(tnt)
                (entity as Arrow).startTracking()
            }

            is Snowball -> {
                entity.isGlowing = true
                entity.visualFire = TriState.TRUE
            }

//            is Trident -> {
//                isCancelled = true
//            }
        }
    }

    @ArenaEventHandler
    fun ProjectileHitEvent.handler(competition: LiveCompetition<*>) {
        when (entity) {
            // arrow kills
            is Arrow -> {
//                PLUGIN.logger.info("arrow vel = ${entity.velocity.length()}")


//                (hitEntity as? Damageable)?.damage(9999.0)
                (entity as Arrow).stopTracking()
//                (entity as Arrow).damage = 0.0
                // salmon reflect can make it faster, make sure to clamp
                val power = this.entity.velocity.length().toFloat().remapClamped(.3f, 3f, 0f, 3f)
                entity.world.createExplosion(entity, power, false)
                entity.remove() // dont stick
            }

            // death snowball
            is Snowball -> {
                (hitEntity as? Damageable)?.damage(9999.0, entity.shooter as Player, DamageType.LIGHTNING_BOLT)
                entity.world.strikeLightning(entity.location)
            }

            is WindCharge -> {
//                PLUGIN.logger.info("cancelling wind charge")
//                isCancelled = true

                // sideways movement on top of existing windcharge behavior
                competition.players.forEach {
                    if (it.player.location.distance(entity.location) < 4) {
                        val dir = it.player.location.subtract(entity.location).toVector().normalize()
                        val len = it.player.location.subtract(entity.location).toVector().length()
                        dir.y = 0.0
                        it.player.velocity = dir.multiply(len.toFloat().remapClamped(0f, 4f, 3f, 0f))
                    }
                }
            }

            is EnderPearl -> {
                val display = entity.passengers.firstOrNull() as? BlockDisplay ?: return
                isCancelled = true
                entity.world.createExplosion(
                    entity,
                    if (display.transformation.scale == Vector3f(.5f)) 2f else 5f,
                    true
                )
                display.remove()
                entity.remove()
            }
        }

    }

    @ArenaEventHandler
    fun PlayerInteractEvent.handler(competition: LiveCompetition<*>) {
        // BUG: off hand exists :P
        // too bad i dont care
        when (player.inventory.itemInMainHand) {
            Items.MACE.item -> {
                // only left click works.
                // BUG: left click air erroneously happens with other stuff like throwing items. it's fine
                if (action.isRightClick) {
                    // shoot wind charge. it has the most fun movement. if its too OP, use fireball
                    player.launchProjectile(WindCharge::class.java, player.velocityZeroGround.add(player.eyeLocation.direction))
                    // cancel so player doesnt break anything
                    //        isCancelled = true

                } else if (action.isLeftClick) {

                    PLUGIN.logger.info("mace vel speed = ${player.velocity.length()}")
                    if (
//                        player.fallDistance > 5
                        player.velocity.length() > 1
                    ) {
                        if (player.hasCooldown(player.inventory.itemInMainHand)) {
                            player.world.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, .5f)
                            return
                        }

                        val nearbyEntities = player.world.getNearbyEntities(
                            // like airblast, check in front of where we're looking
                            player.eyeLocation.add(player.eyeLocation.direction.multiply(2)),
                            2.0, 2.0, 2.0,
                            { it is Damageable && it != player }
                        )
                        if (nearbyEntities.isNotEmpty()) {
                            PLUGIN.logger.info("mace HIT")

                            // mimic mace effect but bigger radius
                            player.isGliding = false
                            player.velocity = player.velocity.multiply(-1.5)
                            nearbyEntities.forEach { (it as Damageable).damage(20.0, player, DamageType.MACE_SMASH) }
                            player.world.strikeLightning(player.location)
                            player.fallDistance = 0f
                            player.world.playSound(player, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 1f)

                        } else {
                            player.world.playSound(player, Sound.ITEM_MACE_SMASH_AIR, 1f, 1f)
                            PLUGIN.logger.info("mace miss")
                        }

                        player.setCooldown(Items.MACE.item, 10)
//                        dontMace.add(player)
//                        Bukkit.getScheduler().runTaskLater(PLUGIN, Runnable { dontMace.remove(player) }, 10)
                    }
                }
            }

            Items.TNT.item -> {
                if (action == Action.PHYSICAL) return
                if (player.hasCooldown(player.inventory.itemInMainHand)) {
                    player.world.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, .5f)
                    return
                }
                val small = action.isRightClick

                val projectile = player.launchProjectile(EnderPearl::class.java, player.velocityZeroGround.add(player.eyeLocation.direction.multiply(.7)))
                val tnt = projectile.world.spawn(projectile.location, BlockDisplay::class.java)
                tnt.block = Material.TNT.createBlockData()
                tnt.transformation = Transformation(
                    Vector3f(-.5f).mul(if (small) .5f else 1f),
                    Quaternionf(),
                    Vector3f(1f).mul(if (small) .5f else 1f),
                    Quaternionf()
                )
                projectile.addPassenger(tnt)

                player.world.playSound(player, Sound.ENTITY_TNT_PRIMED, 1f, if (small) 1f else .5f)
                player.setCooldown(player.inventory.itemInMainHand, 20 * 5)
            }

            Items.SALMON.item -> {
                if (!action.isLeftClick) return
                if (player.hasCooldown(player.inventory.itemInMainHand)) {
                    player.world.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, .5f)
                    return
                }


                val nearbyEntities = player.world.getNearbyEntities(
                    player.eyeLocation.add(player.eyeLocation.direction.multiply(3)),
                    3.0, 3.0, 3.0,
                    { it != player }
                )
                for (it in nearbyEntities) {
                    if (it is Projectile && it.shooter == player) continue // cant hit your own things
                    it.velocity = this.player.eyeLocation.direction.multiply(5)
                    if (it is Arrow) it.startTracking() // set new velocity
                    it.fireTicks = 20 * 5
                    if (it is Player) it.isMarkedForDeath = true
                    if (it is Projectile) it.shooter = player // to count the kill
                    player.attack(it)
                }
                if (nearbyEntities.isEmpty()) { // make sure to indicate whiff with sound
                    player.world.playSound(player, Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 1f, 1f)
                }
                // BUG: if you directly hit it does nothing lol

                player.setCooldown(player.inventory.itemInMainHand, 10)
            }

            Items.ARROW.item -> {
                if (!action.isLeftClick) return

                player.launchProjectile(WitherSkull::class.java, player.velocityZeroGround.add(player.eyeLocation.direction))
            }

            Items.HORN.item -> {
                if (!action.isRightClick) return
                // BUG: doesnt make horn sound???? why???

                if (player.hasCooldown(player.inventory.itemInMainHand)) {
                    player.world.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, .5f)
                    return
                }

                competition.players.forEach {
                    it.player.setCooldown(player.inventory.itemInMainHand, 20 * 30)
                }

                // wait and then get players again in case they leave
                Bukkit.getScheduler().runTaskLater(PLUGIN, Runnable {
                    competition.players.forEach {
                        it.player.damage(9999.0, null, DamageType.MAGIC)
                        it.player.showTitle(Title.title(Component.text("Shuffle!"), Component.empty()))
                        it.player.world.strikeLightning(it.player.location)
                    }
                }, 20 * 3)
            }


            Items.SPYGLASS.item -> {
                if (!action.isLeftClick) return
                if (player.hasCooldown(player.inventory.itemInMainHand)) {
                    player.world.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, .5f)
                    return
                }

//                val projectile = player.launchProjectile(Arrow::class.java, player.velocityZeroGround.add(player.eyeLocation.direction.multiply(1000)))

                player.rayTraceEntities(120)?.hitEntity?.let {
                    (it as? Damageable)?.damage(20.0, player)
                }

                player.world.playSound(player, Sound.ITEM_WOLF_ARMOR_DAMAGE, 1f, 1f)
                player.setCooldown(player.inventory.itemInMainHand, 20)
            }
        }
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
    fun EntityDamageEvent.handler() {
        if (cause == DamageCause.FIRE_TICK) {
//            PLUGIN.logger.info("fire tick on ${entity.name}")

//            val unitRandom = Vector.getRandom().multiply(2).subtract(Vector(1, 1, 1))
//            entity.velocity = unitRandom.multiply(.5)
        }

        if (damageSource.directEntity is WitherSkull) {
            // wither skulls do NOTHING
            isCancelled = true
            // just kidding they do a little bit
            (entity as Damageable).damage(3.0, damageSource.causingEntity, DamageType.WITHER_SKULL)
            return
        }

        if (this.entity !is Player) return // the rest should only apply to players

        PLUGIN.logger.info("${entity.name} lost $damage health from $cause")

        // revert non lethal damage only for hit ground and wall. everything else should be normal damage
        if (cause == DamageCause.FALL || cause == DamageCause.FLY_INTO_WALL) {
            // revert non-lethal damage
            if ((entity as Player).health - damage > 0) damage = 0.0
        }

        if ((entity as Player).hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            // invisible = on cooldown. no hurt
            isCancelled = true
            return
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
                // arena restore puts back entities, so lets remove current ones
                val map = competition.map as LiveCompetitionMap
                val bounds = map.bounds!!
                for (entity in map.world.entities) {
                    if (bounds.isInside(entity.boundingBox) && entity !is Player) {
                        entity.remove()
                    }
                }
            }
        }
    }

    @ArenaEventHandler
    fun ArenaJoinEvent.handler() {
        if (this.competition.players.count() == 1) {
            Bukkit.broadcast(
                Component.text("Arena ${this.competition.map.name} has someone in it! Click to join")
                    .color(NamedTextColor.GOLD)
                    .clickEvent(ClickEvent.runCommand("/gg join ${this.competition.map.name}"))
            )
        }

        player.saturation = 9999f
        player.saturatedRegenRate = 20
        player.unsaturatedRegenRate = 20

        player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE)!!.baseValue = 9999.0

        trackedMacePlayers.add(player)

        // if everyone is in, just start the game
        if (this.competition.players.count() == Bukkit.getOnlinePlayers().count())
            Bukkit.getScheduler().runTaskLater(PLUGIN, Runnable {
                this.competition.phaseManager.setPhase(CompetitionPhaseType.INGAME, true)
            }, 10);
    }

    @ArenaEventHandler
    fun ArenaLeaveEvent.handler() {
//        val team = Bukkit.getScoreboardManager().mainScoreboard.getTeam("GravityGuild")!!
//        team.removePlayer(player)

        trackedMacePlayers.remove(player)

        playerLastDamager.remove(player)

        // because our victory condition is only time limit, it doesnt close early. we gotta do this ourselves
        if (this.competition.players.count() <= 1) {
            this.competition.phaseManager.setPhase(CompetitionPhaseType.VICTORY, true)
            // actually trigger the victory for that player :P
            (this.competition.phaseManager.currentPhase as VictoryPhase).onVictory(setOf(this.competition.players.first()))
        }
    }

    @ArenaEventHandler
    fun ArenaRespawnEvent.handler() {
        when (competition.phase) {
            CompetitionPhaseType.INGAME -> {
                //            player.initAndSpawn()
            }
        }

        dontGlide.remove(player)

        // okay, now use our custom killer thing to track kills
        // TODO: maybe move this to PlayerDeathEvent if we wait before respawning
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
    }

    // editing player stuff has to happen here, so says the docs
    @ArenaEventHandler
    fun PlayerPostRespawnEvent.handler() {

        player.saturation = 9999f
        player.saturatedRegenRate = 20
        player.unsaturatedRegenRate = 20

        // these persist but after death but not visually, so this makes the visual appear
        Items.entries.forEach { player.setCooldown(it.item, player.getCooldown(it.item)) }

        // this does cooldown
        // BUG: doesnt hide clothes
        player.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, 20 * 3, 1, false, false))
        // just to get ur surroundings
        player.addPotionEffect(PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 3, 1, false, false))
    }

    //    data class PlayerDamageData(var lastDamagedTick: Int, var totalDamage: Double)

    val playerLastDamager = mutableMapOf<Player, Pair<Player, Int>>()

    @ArenaEventHandler
    fun ArenaKillEvent.handler() {
        // cancel out kill increment that StatListener did. we do our own thing on respawn
        this.killer.computeStat(ArenaStats.KILLS) { old: Int? -> (old ?: 0) - 1 }
    }

    enum class Items(val item: ItemStack) {
        BOW(ItemStack.of(Material.BOW).apply {
            addUnsafeEnchantment(Enchantment.INFINITY, 1)
            addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)
            addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1)

            lore(listOf(Component.text("Shoots explosive antigravity arrows. Power is based on speed. Also knocks players out of elytra").color(NamedTextColor.BLUE)))
        }),
        ARROW(ItemStack.of(Material.ARROW).apply {
            addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)
            addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1)

            lore(listOf(Component.text("Shoots wither skulls").color(NamedTextColor.BLUE)))
        }),
        TNT(ItemStack.of(Material.TNT).apply {
            addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)
            addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1)

            lore(listOf(Component.text("Shoots grenades. Left click for big, right click for small").color(NamedTextColor.BLUE)))
        }),
        MACE(ItemStack.of(Material.MACE).apply {
            addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)
            addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1)

            lore(listOf(Component.text("Left click to rocket jump, right click while at speed to smash entities in an area").color(NamedTextColor.BLUE)))
        }),
        SALMON(ItemStack.of(Material.SALMON).apply {
            // BUG: knockback doesnt work sometimes?
//            addUnsafeEnchantment(Enchantment.KNOCKBACK, 9999)
//            addUnsafeEnchantment(Enchantment.FIRE_ASPECT, 2)
            addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)
            addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1)

            lore(listOf(Component.text("Slap anything in an area away from you and light them on fire").color(NamedTextColor.BLUE)))
        }),
        HORN(ItemStack.of(Material.GOAT_HORN).apply {
            addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)
            addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1)

            lore(listOf(Component.text("Shuffle!").color(NamedTextColor.BLUE)))

            @Suppress("UnstableApiUsage")
            this.setData(DataComponentTypes.INSTRUMENT, MusicInstrument.CALL_GOAT_HORN)
        }),
        HELMET(ItemStack.of(Material.END_ROD).apply {
            addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)
            addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1)
        }),
        CHESTPLATE(ItemStack.of(Material.ELYTRA).apply {
            addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)
            addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1)
        }),

        SPYGLASS(ItemStack.of(Material.SPYGLASS).apply {
            addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)
            addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1)

            lore(listOf(Component.text("Hitscan shot on left click").color(NamedTextColor.BLUE)))
        })
    }

    fun Player.initInventory() {
//        addPotionEffect(PotionEffect(PotionEffectType.NIGHT_VISION, Int.MAX_VALUE, 1, false, false))

        // init inventory
        inventory.clear() // sometimes its not cleared when it should be. lets just ensure it is
//        inventory.setItem(0, Items.item0)
//        inventory.setItem(1, Items.item1)
        inventory.addItem(Items.BOW.item)
        inventory.addItem(Items.MACE.item)
        inventory.addItem(Items.SALMON.item)
        inventory.addItem(Items.TNT.item)
        inventory.addItem(Items.ARROW.item)
//        inventory.addItem(Items.HORN.item)
//        inventory.addItem(Items.SPYGLASS.item)
        inventory.helmet = Items.HELMET.item
        inventory.chestplate = Items.CHESTPLATE.item

        // TODO: teleport to random part on the map? or just use manual spawns like currently
    }

    fun Player.holdingItem(item: ItemStack) = inventory.itemInMainHand == item || inventory.itemInOffHand == item

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

    val trackedMacePlayers = mutableListOf<Player>()

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
    if (source != null) builder = builder.withDirectEntity(source)
    this.damage(amount, builder.build())
}