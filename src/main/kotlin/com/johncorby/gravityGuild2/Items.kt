package com.johncorby.gravityGuild2

import com.johncorby.gravityGuild2.ArrowTracker.startTracking
import com.johncorby.gravityGuild2.ArrowTracker.stopTracking
import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import net.kyori.adventure.util.TriState
import org.battleplugins.arena.ArenaPlayer
import org.battleplugins.arena.competition.LiveCompetition
import org.battleplugins.arena.stat.ArenaStats
import org.bukkit.*
import org.bukkit.damage.DamageType
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.*
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f


object Mace {
    val trackedPlayers = mutableListOf<Player>()

    init {
        Bukkit.getScheduler().runTaskTimer(PLUGIN, Runnable {
            trackedPlayers.forEach {
                it.exp = it.velocity.length().toFloat().remapClamped(0f, 1f, 0f, 1f)
                it.level = ArenaPlayer.getArenaPlayer(it)!!.getStat<Int>(ArenaStats.KILLS)!!
            }
        }, 0, 0)
    }

    fun smash(player: Player) {

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
                player.world.strikeLightningEffect(player.location)
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

    fun launch(player: Player) {
        // shoot wind charge. it has the most fun movement. if its too OP, use fireball
        player.launchProjectile(WindCharge::class.java, player.velocityZeroGround.add(player.eyeLocation.direction))
        // cancel so player doesnt break anything
        //        isCancelled = true

    }


    fun hit(entity: Projectile, competition: LiveCompetition<*>) {
//                PLUGIN.logger.info("cancelling wind charge")
//                isCancelled = true

        // sideways movement on top of existing windcharge behavior
        competition.players.forEach {
            val windToPlayer = it.player.location.subtract(entity.location.add(Vector(0.0, -0.5, 0.0))).toVector()
            val len = windToPlayer.length()
            if (len < 3) {
                val dir = windToPlayer.normalize()
                it.player.velocity = it.player.velocity.add(dir.multiply(len.toFloat().remapClamped(3f, 0f, 0f, 3f)))
            }
        }
    }

}


// use for movement cancel else it thinks ur falling slightly when ur not
@Suppress("DEPRECATION")
val Player.velocityZeroGround get() = if (isOnGround) Vector(0, 0, 0) else velocity


object Tnt {
    fun launch(player: Player, small: Boolean) {
        if (player.hasCooldown(player.inventory.itemInMainHand)) {
            player.world.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, .5f)
            return
        }

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

    fun hit(entity: Projectile) {
        val display = entity.passengers.firstOrNull() as? BlockDisplay ?: return
        entity.world.createExplosion(
            entity,
            if (display.transformation.scale == Vector3f(.5f)) 2f else 5f,
            true
        )
        display.remove()
        entity.remove()
    }
}


object GGBow {
    fun launch(entity: Projectile) {
        entity.setGravity(false)
        entity.visualFire = TriState.TRUE
//                val tnt = entity.world.spawn(entity.location, TNTPrimed::class.java)
//                entity.addPassenger(tnt)
        (entity as Arrow).startTracking()
    }

    fun hit(entity: Projectile) {
        //                PLUGIN.logger.info("arrow vel = ${entity.velocity.length()}")


//                (hitEntity as? Damageable)?.damage(9999.0)
        (entity as Arrow).stopTracking()
//                (entity as Arrow).damage = 0.0
        // salmon reflect can make it faster, make sure to clamp
        val power = entity.velocity.length().toFloat().remapClamped(.3f, 3f, .1f, 3f)
        entity.world.createExplosion(entity, power, false)
        entity.remove() // dont stick
    }
}

object Fish {
    fun attack(player: Player) {
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
            if (it is Arrow && it.shooter == player) continue // cant hit your own things
            it.velocity = player.eyeLocation.direction.multiply(5)
            if (it is Arrow) it.startTracking() // set new velocity
            it.fireTicks = 20 * 10
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
}

object Knife {
    fun attack(player: Player) {
        val nearbyEntities = player.world.getNearbyEntities(
            player.eyeLocation.add(player.eyeLocation.direction.multiply(2)),
            2.0, 2.0, 2.0,
            { it is Damageable && it != player }
        )
        for (nearbyEntity in nearbyEntities) {
            // literally stealing from https://www.youtube.com/watch?v=gh5Fg5d_uBU
            val victimView = (nearbyEntity as LivingEntity).eyeLocation.direction
            victimView.y = 0.0
            val spyView = player.eyeLocation.direction
            spyView.y = 0.0
            val deltaPosition = nearbyEntity.location.subtract(player.location).toVector()
            deltaPosition.y = 0.0

            val `behind the victim` = Math.toDegrees(victimView.angle(deltaPosition).toDouble()) < 90
            val `looking towards the victim` = Math.toDegrees(spyView.angle(deltaPosition).toDouble()) < 60
            val `facing same dierction` = Math.toDegrees(spyView.angle(victimView).toDouble()) < 107.5

            if (`behind the victim` && `looking towards the victim` && `facing same dierction`)
                nearbyEntity.damage(9999.0, player)
        }
    }

    fun launch(player: Player) {
        player.launchProjectile(WitherSkull::class.java, player.velocityZeroGround.add(player.eyeLocation.direction))
    }
}


object Horn {

    fun use(player: Player, competition: LiveCompetition<*>) {
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
                it.player.world.strikeLightningEffect(it.player.location)
            }
        }, 20 * 3)

    }
}

object Gun {
    fun use(player: Player) {
        if (player.hasCooldown(player.inventory.itemInMainHand)) {
            player.world.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, .5f)
            return
        }

//                val projectile = player.launchProjectile(Arrow::class.java, player.velocityZeroGround.add(player.eyeLocation.direction.multiply(1000)))

        val result = player.world.rayTrace(player.location, player.location.direction, 120.0, FluidCollisionMode.NEVER, true, 10.0, null)
        result?.hitEntity?.let {
            (it as? Damageable)?.damage(20.0, player)
        }

        player.world.playSound(player, Sound.ITEM_WOLF_ARMOR_DAMAGE, 1f, 1f)
        player.setCooldown(player.inventory.itemInMainHand, 20)

    }
}


object GGSnowball {
    fun launch(entity: Projectile) {
        entity.isGlowing = true
        entity.visualFire = TriState.TRUE
    }

    fun hit(entity: Projectile, hitEntity: Entity?) {
        (hitEntity as? Damageable)?.damage(9999.0, entity.shooter as Player, DamageType.LIGHTNING_BOLT)
        entity.world.strikeLightningEffect(entity.location)
    }
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
    FISH(ItemStack.of(Material.SALMON).apply {
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
    GUN(ItemStack.of(Material.SPYGLASS).apply {
        addUnsafeEnchantment(Enchantment.UNBREAKING, 9999)
        addUnsafeEnchantment(Enchantment.BINDING_CURSE, 1)

        lore(listOf(Component.text("Hitscan shot on left click").color(NamedTextColor.BLUE)))
    }),


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
    inventory.addItem(Items.HORN.item)
//        inventory.addItem(Items.SPYGLASS.item) TODO: add back when you refactor and add cooldown
    inventory.helmet = Items.HELMET.item
    inventory.chestplate = Items.CHESTPLATE.item

    // TODO: teleport to random part on the map? or just use manual spawns like currently
}
