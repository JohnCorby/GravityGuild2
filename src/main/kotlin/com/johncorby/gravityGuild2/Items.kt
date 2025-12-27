// organizes data and logic by item. basically just a namespacing method


package com.johncorby.gravityGuild2

import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import net.kyori.adventure.util.TriState
import org.battleplugins.arena.ArenaPlayer
import org.battleplugins.arena.competition.LiveCompetition
import org.bukkit.*
import org.bukkit.damage.DamageSource
import org.bukkit.damage.DamageType
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.*
import org.bukkit.inventory.ItemStack
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.metadata.Metadatable
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.absoluteValue
import kotlin.random.Random

///////// regular items /////////////

object GGMace {
    val trackedPlayers = mutableListOf<Player>()

    init {
        Bukkit.getScheduler().runTaskTimer(PLUGIN, Runnable {
            trackedPlayers.forEach {
                it.exp = it.velocity.length().toFloat().remapClamped(0f, 1f, 0f, 1f)
                it.level = it.velocity.length().toInt()

                if (it.velocity.length() > 1 && it.inventory.itemInMainHand != Items.ARROW.item)
                    for (otherPlayer in ArenaPlayer.getArenaPlayer(it)!!.competition.players)
                        if (otherPlayer.player != it)
                            otherPlayer.player.playSound(it, Sound.BLOCK_NOTE_BLOCK_BANJO, it.velocity.length().toFloat(), 1f)
            }
        }, 0, 0)
    }

    fun smash(player: Player) {

        PLUGIN.logger.info("mace vel speed = ${player.velocity.length()}")
        if (
//                        player.fallDistance > 5
            player.velocity.length() > 1
        ) {
            if (player.doItemCooldown(20)) return

            val nearbyEntities = player.checkHitbox(3.0)
            if (nearbyEntities.any { it is Damageable }) {
                PLUGIN.logger.info("mace HIT")

                // mimic mace effect but bigger radius
//                player.isGliding = false
//                player.velocity = player.velocity.multiply(-1.5)
                player.velocity = Vector(player.velocity.x * 1.5, player.velocity.y.absoluteValue * 1.5, player.velocity.z * 1.5)
                nearbyEntities.forEach { (it as? Damageable)?.run { damagePrecise(20.0, player, player) } }
//                nearbyEntities.forEach {
//                    (it as? Damageable)?.damage(10.0, player, DamageType.MACE_SMASH)
//                    (it as? Player)?.isMarkedForDeath = true
//                }
                player.world.strikeLightningEffect(player.location)
                player.fallDistance = 0f
                player.world.playSound(player, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 1f)

            } else {
                player.world.playSound(player, Sound.ITEM_MACE_SMASH_AIR, 1f, 1f)
                PLUGIN.logger.info("mace miss")
            }
        }
    }

    fun launch(player: Player) {
        // shoot wind charge. it has the most fun movement. if its too OP, use fireball
        player.launchProjectile(WindCharge::class.java, player.eyeLocation.direction)
        // cancel so player doesnt break anything
        //        isCancelled = true

    }


    fun hit(entity: WindCharge, competition: LiveCompetition<*>) {
//                PLUGIN.logger.info("cancelling wind charge")
//                isCancelled = true

        // sideways movement on top of existing windcharge behavior
        return
        competition.players.forEach {
            val windToPlayer = it.player.location.subtract(entity.location.add(Vector(0.0, -1.0, 0.0))).toVector()
            val len = windToPlayer.length()
            if (len < 4) {
                val dir = windToPlayer.normalize()
                it.player.velocity = it.player.velocity.add(dir.multiply(len.toFloat().remapClamped(4f, 0f, 0f, 2f)))
            }
        }
    }

}


object GGTnt {
    fun launch(player: Player, small: Boolean) {
        if (player.doItemCooldown(if (small) 20 * 10 else 20 * 5)) return

        val projectile = player.launchProjectile(EnderPearl::class.java, player.eyeLocation.direction.multiply(.7))
        val tnt = projectile.world.spawn(projectile.location, BlockDisplay::class.java)
        tnt.block = Material.TNT.createBlockData()
        tnt.transformation = Transformation(
            Vector3f(-.5f).mul(if (small) .5f else 1f),
            Quaternionf(),
            Vector3f(1f).mul(if (small) .5f else 1f),
            Quaternionf()
        )
        projectile.addPassenger(tnt)
        trackedTnt.add(projectile)

        player.world.playSound(player, Sound.ENTITY_TNT_PRIMED, 1f, if (small) 1f else .5f)
    }

    fun hit(entity: EnderPearl): Boolean {
        val display = entity.passengers.firstOrNull() as? BlockDisplay ?: return false
        val small = display.transformation.scale == Vector3f(.5f)
        entity.world.createExplosion(
            entity,
            if (small) 2f else 7f,
            true
        )
        display.remove()
        entity.remove()
        trackedTnt.remove(entity)
        return true
    }

    val trackedTnt = mutableListOf<EnderPearl>()

    init {
        Bukkit.getScheduler().runTaskTimer(PLUGIN, Runnable {
            val iter = trackedTnt.iterator() // so we can remove
            while (iter.hasNext()) {
                val tnt = iter.next()
                val display = tnt.passengers.firstOrNull() as? BlockDisplay ?: continue // i dont know why this happens sometimes but it does
                val small = display.transformation.scale == Vector3f(.5f)

                val nearbyEntities = tnt.world.getNearbyEntities(
                    tnt.location,
                    3.0, 3.0, 3.0,
                    { it != tnt && it != tnt.shooter },
                )
                nearbyEntities.forEach {
                    when {
                        it is Arrow -> {
                            if (small) {
                                // whoever coins the arrow first gets it
                                if (it.hasMetadata("coined")) return@forEach
                                it.setMetadata("coined", null)

                                iter.remove()
                                tnt.hitEntity(it)
                                // ultrakill coin moment. go towards closest player
                                tnt.shooter = it.shooter // arrow shooter steals tnt
                                var players = ArenaPlayer.getArenaPlayer(it.shooter as Player)!!.competition.players.map { it.player }
                                val closestPlayer = players.filter { player -> player != it.shooter }.minByOrNull { player -> it.location.distance(player.location) } ?: return@forEach
                                it.velocity = closestPlayer.location.subtract(it.location).toVector().normalize().multiply(5)
                                GGBow.trackedArrows[it] = it.velocity
                            } else {
                                it.addPassenger(tnt)
                                tnt.shooter = it.shooter // inherit shooter
                                it.velocity = it.velocity.multiply(0.5)
                                GGBow.trackedArrows[it] = it.velocity
                                iter.remove()
                                trackedTnt.remove(tnt)
                            }
                        }

                        it is Player && small -> {
                            tnt.teleport(it.location)
                            iter.remove()
                            tnt.hitEntity(it) // make small tnt useful by being able to hit in midair
                        }
                    }
                }
            }
        }, 0, 0)
    }
}


object GGBow {
    fun launch(entity: Arrow) {
        // dont include player velocity in projectile velocity
//        entity.velocity = entity.velocity.subtract((entity.shooter as Player).velocityZeroGround)

        entity.setGravity(false)
        entity.visualFire = TriState.TRUE
//                val tnt = entity.world.spawn(entity.location, TNTPrimed::class.java)
//                entity.addPassenger(tnt)
        trackedArrows[entity] = entity.velocity
    }

    fun hit(entity: Arrow) {
        //                PLUGIN.logger.info("arrow vel = ${entity.velocity.length()}")

        val maybeTnt = entity.passengers.firstOrNull()
        if (maybeTnt is EnderPearl) GGTnt.hit(maybeTnt)


//                (hitEntity as? Damageable)?.damage(9999.0)
        trackedArrows.remove(entity)
//                (entity as Arrow).damage = 0.0
        // salmon reflect can make it faster, make sure to clamp
        val power = entity.velocity.length().toFloat().remapClamped(.3f, 3f, .1f, 3f)
        entity.world.createExplosion(entity, power, false)
        entity.remove() // dont stick
    }


    // broken with multiple competitions? except its not, it works itself out i think...
    val trackedArrows = mutableMapOf<Arrow, Vector>()

    init {
        Bukkit.getScheduler().runTaskTimer(PLUGIN, Runnable {
            val iter = trackedArrows.iterator() // so we can remove
            while (iter.hasNext()) {
                val (arrow, velocity) = iter.next()
                arrow.velocity = velocity // arrow slows down, retain velocity

                val nearbyEntities = arrow.world.getNearbyEntities(
                    arrow.location,
                    3.0, 3.0, 3.0,
                    { it != arrow && it != arrow.shooter && it is Player && (it.isGliding || it.isMarkedForDeath) },
                )
                nearbyEntities.forEach {
                    val nearbyPlayer = it as Player

                    // was way too op for mace, now its way too op for arrows LOL
//                    arrow.teleport(closestEntity)

                    if (it.isMarkedForDeath) {
                        iter.remove()
                        arrow.hitEntity(nearbyPlayer) // bye bye
                        return@forEach
                    }

                    nearbyPlayer.dontGlide = true
                    nearbyPlayer.damagePrecise(0.0, arrow, arrow.shooter as Player)
                }
            }
        }, 0, 0)
    }
}

object GGFish {
    fun attack(player: Player) {
        if (player.doItemCooldown(20)) return

        var hit = false
        val nearbyEntities = player.checkHitbox(5.0)
        for (it in nearbyEntities) {
            if (it is Projectile && it.shooter == player && it !is EnderPearl) continue // cant hit your own things
            val oldVel = it.velocity
            it.velocity = player.eyeLocation.direction.multiply(if (it is Projectile) 3 else 5)
            hit = true
            if (it is Arrow) GGBow.trackedArrows[it] = it.velocity // set new velocity
            it.fireTicks = 20 * 10
            if (it is Player) {
                if (it.inventory.itemInMainHand == Items.ARROW.item) it.velocity = oldVel // super bad way of writing this but it works
                it.isMarkedForDeath = true

                // TODO: remove if this sucks
                it.dontGlide = true
            }
            if (it is Projectile) {
                if (it.shooter is Player && it.shooter != player) {
                    (it.shooter as Player).isMarkedForDeath = true // for fun

                    // TODO: remove if this sucks
                    it.velocity = (it.shooter as Player).location.subtract(it.location).toVector().normalize().multiply(it.velocity.length())
                    if (it is Arrow)
                        GGBow.trackedArrows[it] = it.velocity
                }
                it.shooter = player // to count the kill
                it.setMetadata("reflected", null)
            }
            (it as? Damageable)?.damagePrecise(0.0, player, player)
        }
        if (!hit) { // make sure to indicate whiff with sound
            player.world.playSound(player, Sound.ENTITY_PLAYER_ATTACK_NODAMAGE, 1f, 1f)
        } else {
            // TODO: remove if this sucks
            player.velocity = player.eyeLocation.direction.multiply(2)
//            player.isGliding = false
        }
    }

    fun launch(player: Player) {
        if (player.doItemCooldown(10)) return

        val puffer = player.world.spawn(player.eyeLocation, PufferFish::class.java)
        puffer.velocity = player.eyeLocation.direction.multiply(2)
        puffer.setMetadata("player", player)
    }

    fun hit(pufferFish: PufferFish, entity: Entity) {
        val player = pufferFish.getMetadata<Player>("player") ?: return
        if (entity == player) return
        (entity as? Damageable)?.damagePrecise(3.0, pufferFish, player)
        (entity as? Player)?.let { it.isMarkedForDeath = true }
    }
}

object GGArrow {
    fun attack(player: Player) {
        // TODO: make 3?
        val nearbyEntities = player.checkHitbox(5.0)
        for (nearbyEntity in nearbyEntities) {
            if (nearbyEntity !is LivingEntity) return

            // literally stealing from https://www.youtube.com/watch?v=gh5Fg5d_uBU
            val victimView = nearbyEntity.eyeLocation.direction
            victimView.y = 0.0
            val spyView = player.eyeLocation.direction
            spyView.y = 0.0
            val deltaPosition = nearbyEntity.location.subtract(player.location).toVector()
            deltaPosition.y = 0.0

            val `behind the victim` = Math.toDegrees(victimView.angle(deltaPosition).toDouble()) < 90
            val `looking towards the victim` = Math.toDegrees(spyView.angle(deltaPosition).toDouble()) < 60
            val `facing same dierction` = Math.toDegrees(spyView.angle(victimView).toDouble()) < 107.5

            if (`behind the victim` && `looking towards the victim` && `facing same dierction`)
                nearbyEntity.damagePrecise(9999.0, player, player)
        }
    }

    fun launch(player: Player) {
        player.launchProjectile(WitherSkull::class.java, player.eyeLocation.direction.multiply(5))
    }

    fun hit(entity: Entity, witherSkull: WitherSkull) {
        if (entity == witherSkull.shooter) return
        (entity as? Damageable)?.damagePrecise(3.0, witherSkull, witherSkull.shooter as Player)
        (entity as? Player)?.isMarkedForDeath = true
    }
}


////////////// party items ////////////////

object GGShuffleHorn {

    fun use(player: Player, competition: LiveCompetition<*>) {
        if (player.hasCooldown(player.inventory.itemInMainHand)) {
            player.world.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, .5f)
            return
        }

        // wait one frame to cooldown so horn sound still plays
        Bukkit.getScheduler().runTask(PLUGIN, Runnable {
            competition.players.forEach {
                it.player.setCooldown(player.inventory.itemInMainHand, 20 * 30)
            }
        })

        // wait and then get players again in case they leave
        Bukkit.getScheduler().runTaskLater(PLUGIN, Runnable {
            competition.players.forEach {
                it.player.damage(9999.0)
                it.player.showTitle(Title.title(Component.text("Shuffle!"), Component.empty()))
                it.player.world.strikeLightningEffect(it.player.location)
            }
        }, 20 * 3)

    }
}

object GGGun {
    fun use(player: Player) {
        if (player.doItemCooldown(20)) return

//                val projectile = player.launchProjectile(Arrow::class.java, player.eyeLocation.direction.multiply(1000))

        val result = player.world.rayTrace(player.location, player.location.direction, 120.0, FluidCollisionMode.NEVER, true, 10.0, null)
        result?.hitEntity?.let {
            (it as? Damageable)?.damagePrecise(20.0, player, player)
        }

        player.world.playSound(player, Sound.ITEM_WOLF_ARMOR_DAMAGE, 1f, 1f)

    }

    fun attack(entity: Entity?, player: Player) {
        if (player.doItemCooldown(20 * 2)) return

        (entity as? Damageable)?.damagePrecise(9999.0, player, player)

        player.world.playSound(player, Sound.ITEM_WOLF_ARMOR_DAMAGE, 1f, 1f)
    }
}


object GGSnowball {
    fun launch(entity: Snowball) {
        entity.velocity = entity.velocity.subtract((entity.shooter as Player).velocityZeroGround)

        entity.isGlowing = true
        entity.visualFire = TriState.TRUE
    }

    fun hit(snowball: Snowball, hitEntity: Entity?) {
        (hitEntity as? Damageable)?.damagePrecise(9999.0, snowball, snowball.shooter as Player)
        snowball.world.strikeLightningEffect(snowball.location)

        if (hitEntity is Player) hitEntity.inventory.forEach { hitEntity.setCooldown(it, 20 * 5) }
    }
}

object GGTree {
    val playerLastPlanted = mutableMapOf<Player, Int>()

    fun plant(player: Player) {
        var created = false
        (player.rayTraceEntities(120, true) ?: player.rayTraceBlocks(120.0, FluidCollisionMode.NEVER))
            ?.let {
                val time = Bukkit.getCurrentTick() - (playerLastPlanted[player] ?: Bukkit.getCurrentTick())
                created = player.world.generateTree(it.hitPosition.toLocation(player.world), ThreadLocalRandom.current(), if (time > 20) TreeType.MEGA_REDWOOD else TreeType.BIG_TREE)
            }
        if (created) {
            playerLastPlanted[player] = Bukkit.getCurrentTick()
            player.setCooldown(player.inventory.itemInMainHand, 20)
        } else {
            player.world.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, .5f)
        }
    }
}

object GGGlowberry {
    fun eat(player: Player) {

        for (otherPlayer in player.world.players) {
            if (otherPlayer == player) continue

            otherPlayer.isMarkedForDeath = true // maybe make this non mark for death glow but this is funnier
        }
    }
}

////////// item management /////////////

enum class Items(val item: ItemStack, val partyWeight: Double? = null) {
    BOW(ItemStack.of(Material.CROSSBOW).apply {
        addUnsafeEnchantment(Enchantment.INFINITY, 1)
        addUnsafeEnchantment(Enchantment.QUICK_CHARGE, 1)
//        addUnsafeEnchantment(Enchantment.MULTISHOT, 1)
        addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)
        addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1)

        lore(listOf(Component.text("Shoots explosive antigravity arrows. Knocks players out of elytra.").color(NamedTextColor.BLUE)))
    }),
    ARROW(ItemStack.of(Material.ARROW).apply {
        addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)
        addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1)

        lore(listOf(Component.text("Shoots wither skulls on right click. Become silent and backstab on left click.").color(NamedTextColor.BLUE)))
    }),
    TNT(ItemStack.of(Material.TNT).apply {
        addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)
        addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1)

        lore(listOf(Component.text("Shoots grenades. Left click for big, right click for small. Has interactions with arrow.").color(NamedTextColor.BLUE)))
    }),
    MACE(ItemStack.of(Material.MACE).apply {
        addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)
        addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1)

        lore(listOf(Component.text("Left click to rocket jump. Right click while at speed to smash entities in an area").color(NamedTextColor.BLUE)))
    }),
    FISH(ItemStack.of(Material.SALMON).apply {
        addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)
        addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1)

        lore(
            listOf(
                Component.text("Slap anything in an area away from you and mark them for death (glowing and more vulnerable to arrows)").color(NamedTextColor.BLUE),
                Component.text("Right click for pufferfish").color(NamedTextColor.BLUE)
            )
        )
    }),


    NO_PARTY_ITEM(ItemStack.empty(), 1.0),
    SNOWBALLS(ItemStack.of(Material.SNOWBALL, 32), 1.0),
    SHUFFLE_HORN(ItemStack.of(Material.GOAT_HORN).apply {
        addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)

        lore(listOf(Component.text("Shuffle!").color(NamedTextColor.BLUE)))

        @Suppress("UnstableApiUsage")
        this.setData(DataComponentTypes.INSTRUMENT, MusicInstrument.CALL_GOAT_HORN)
    }, 0.3),
    GUN(ItemStack.of(Material.SPYGLASS).apply {
        addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)

        lore(listOf(Component.text("Long punch that insta kills").color(NamedTextColor.BLUE)))
    }, 0.3),
    TREE(ItemStack.of(Material.OAK_SAPLING).apply {
        addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)

        lore(listOf(Component.text("Plant a tree on left click. Longer wait = bigger tree").color(NamedTextColor.BLUE)))
    }, 0.5),

    // TODO teleport enderpearl
    GLOWBERRY(ItemStack.of(Material.GLOW_BERRIES, 3).apply {
        addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)

        lore(listOf(Component.text("Eat to make everyone glow so you can KILL THEM").color(NamedTextColor.BLUE)))
    }, 1.0),


    HELMET(ItemStack.of(Material.END_ROD).apply {
        addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)
        addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1)
    }),

    CHESTPLATE(ItemStack.of(Material.ELYTRA).apply {
        addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)
        addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1)
    });
}

fun Player.initInventory() {
//        addPotionEffect(PotionEffect(PotionEffectType.NIGHT_VISION, Int.MAX_VALUE, 1, false, false))

    // init inventory
    inventory.clear() // sometimes its not cleared when it should be. lets just ensure it is
//        inventory.setItem(0, Items.item0)
//        inventory.setItem(1, Items.item1)
    inventory.addItem(Items.BOW.item)
    inventory.addItem(Items.MACE.item)
    inventory.addItem(Items.FISH.item)
    inventory.addItem(Items.TNT.item)
    inventory.addItem(Items.ARROW.item)
//    inventory.addItem(Items.HORN.item)
//    inventory.addItem(Items.GUN.item)
//    inventory.addItem(Items.TREE.item)
    inventory.helmet = Items.HELMET.item
    inventory.chestplate = Items.CHESTPLATE.item

    // TODO: teleport to random part on the map? or just use manual spawns like currently
}

fun Player.givePartyItem() {
    // https://dev.to/jacktt/understanding-the-weighted-random-algorithm-581p
    val partyItems = Items.entries.filter { it.partyWeight != null && (it.item.isEmpty || it.item !in inventory) }

    val totalWeight = partyItems.sumOf { it.partyWeight!! }
    val random = Random.nextDouble(totalWeight)

    var total = 0.0
    var partyItem: Items? = null
    for (item in partyItems) {
        total += item.partyWeight!!
        if (total >= random) {
            partyItem = item
            PLUGIN.logger.info("choosing party item $partyItem")
            break
        }
    }

    if (partyItem == Items.NO_PARTY_ITEM) return
    inventory.addItem(partyItem!!.item)
    sendMessage(Component.text("You got party item ${partyItem}!"))
}

///////// utils used by items ///////////////

// use for movement cancel else it thinks ur falling slightly when ur not
@Suppress("DEPRECATION")
val Player.velocityZeroGround get() = if (isOnGround) Vector(0, 0, 0) else velocity


var Player.isMarkedForDeath: Boolean
    get() = this.hasPotionEffect(PotionEffectType.GLOWING)
    set(value) {
        if (value) this.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, 10 * 20, 1, false, false))
        else this.removePotionEffect(PotionEffectType.GLOWING)
    }

var Player.dontGlide: Boolean
    get() = this.hasPotionEffect(PotionEffectType.SLOWNESS)
    set(value) {
        if (value) {
            this.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 20, 1, false, false))
            isGliding = false
            world.playSound(this, Sound.ENTITY_PLAYER_BURP, 1f, 1f)
        } else this.removePotionEffect(PotionEffectType.SLOWNESS)
    }

var Player.isRespawning: Boolean
    get() = hasPotionEffect(PotionEffectType.INVISIBILITY)
    set(value) {
        if (value) {
            // respawn
            val competition = ArenaPlayer.getArenaPlayer(this)!!.competition
            val spawns = competition.map.spawns!!.teamSpawns!!["Default"]!!.spawns!!
            val spawn = spawns[Random.nextInt(spawns.size)]
            this.teleport(spawn.toLocation(competition.map.world))

            clearActivePotionEffects()
            fireTicks = 0
            @Suppress("UnstableApiUsage")
            combatTracker.resetCombatState() // to reset death message

            // BUG: doesnt hide clothes
            addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, 20 * 3, 1, false, false))
            // just to get ur surroundings
            addPotionEffect(PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 3, 1, false, false))
        } else {
            removePotionEffect(PotionEffectType.INVISIBILITY)
            removePotionEffect(PotionEffectType.NIGHT_VISION)
        }
    }


fun Player.doItemCooldown(ticks: Int): Boolean {
    if (this.hasCooldown(inventory.itemInMainHand)) {
        this.world.playSound(this, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, .5f)
        return true
    }

    setCooldown(inventory.itemInMainHand, ticks)
    return false
}

fun Player.checkHitbox(radius: Double): Collection<Entity> = this.world.getNearbyEntities(
    this.eyeLocation.add(this.eyeLocation.direction.multiply(radius)),
    radius, radius, radius,
    { it != this }
)

fun Damageable.damagePrecise(amount: Double, source: Entity, player: Player) =
    damage(amount, DamageSource.builder(DamageType.GENERIC).withDirectEntity(source).withCausingEntity(player).build())


fun Float.remapClamped(
    inputMin: Float,
    inputMax: Float,
    outputMin: Float,
    outputMax: Float
): Float {
    // Calculate the normalized position of the value within the input range (0 to 1)
    val normalizedValue = (this - inputMin) / (inputMax - inputMin)

    // Map the normalized value to the output range
    return Math.clamp(outputMin + normalizedValue * (outputMax - outputMin), outputMin, outputMax)
}


// ik im not supposed to be using this but idc its nice
inline fun <reified T> Metadatable.getMetadata(key: String) = (this.getMetadata(key).firstOrNull { it.owningPlugin == PLUGIN && it.value() is T })?.value() as? T
fun <T> Metadatable.setMetadata(key: String, value: T) = this.setMetadata(key, FixedMetadataValue(PLUGIN, value))