package com.qc.aeonis.entity

import com.qc.aeonis.config.AeonisFeatures
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.DifficultyInstance
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.SpawnGroupData
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.npc.Villager
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.Level
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.ServerLevelAccessor
import java.util.EnumSet

class CopperStalkerEntity(entityType: EntityType<out CopperStalkerEntity>, level: Level) : Monster(entityType, level) {
    // Invisibility triggers at health <= 5, lasts 3 seconds (60 ticks)
    private val lowHealthThreshold = 5f
    private val invisDurationTicks = 60 // 3 seconds
    private var swordEnchanted = false
    
    // Track if currently fleeing (during invisibility)
    private var isFleeing = false
    private var fleeTicksRemaining = 0
    
    // Sunburn damage tracking
    private var sunburnTicks = 0

    override fun registerGoals() {
        goalSelector.addGoal(0, FloatGoal(this))
        // Flee goal has higher priority when active
        goalSelector.addGoal(1, CopperStalkerFleeGoal(this))
        goalSelector.addGoal(2, ExtendedReachMeleeGoal(this, 0.85, true, 5.0))
        goalSelector.addGoal(7, WaterAvoidingRandomStrollGoal(this, 0.9))
        goalSelector.addGoal(8, LookAtPlayerGoal(this, Player::class.java, 12f))
        goalSelector.addGoal(9, RandomLookAroundGoal(this))

        targetSelector.addGoal(1, HurtByTargetGoal(this).setAlertOthers())
        targetSelector.addGoal(2, NearestAttackableTargetGoal(this, Player::class.java, true))
        targetSelector.addGoal(3, NearestAttackableTargetGoal(this, Villager::class.java, true))
    }

    override fun aiStep() {
        super.aiStep()
        
        if (!level().isClientSide) {
            // Sunlight burning - more sensitive than zombies
            handleSunlightDamage()
            
            // Low health invisibility + flee behavior
            if (health <= lowHealthThreshold && !isFleeing) {
                // Start fleeing with invisibility
                isFleeing = true
                fleeTicksRemaining = invisDurationTicks
                addEffect(MobEffectInstance(MobEffects.INVISIBILITY, invisDurationTicks, 0, false, false))
                addEffect(MobEffectInstance(MobEffects.SPEED, invisDurationTicks, 1, false, false)) // Speed boost to flee
                // Clear current target so it runs away
                target = null
            }
            
            // Track flee duration
            if (isFleeing) {
                fleeTicksRemaining--
                // Slowly regenerate health while fleeing (0.5 health per second = 1 health per 40 ticks)
                if (tickCount % 40 == 0) {
                    heal(1f)
                }
                
                if (fleeTicksRemaining <= 0) {
                    isFleeing = false
                }
            }
        }
    }
    
    /**
     * Burns in sunlight - MORE sensitive than zombies
     * Takes damage even with low sky light, and burns faster
     */
    private fun handleSunlightDamage() {
        if (level().isClientSide) return
        
        val serverLevel = level() as? ServerLevel ?: return
        
        // Check if it's daytime
        val dayTime = serverLevel.dayTime % 24000L
        val isDaytime = dayTime < 12000L || dayTime > 23500L
        
        if (!isDaytime) {
            sunburnTicks = 0
            return
        }
        
        // Check if exposed to sky (more sensitive - lower threshold than zombies)
        val blockPos = blockPosition()
        val skyLight = serverLevel.getBrightness(LightLayer.SKY, blockPos)
        
        // Burns at sky light >= 10 (zombies need 15), so much more sensitive
        if (skyLight >= 10 && serverLevel.canSeeSky(blockPos)) {
            // Check if wearing a helmet (provides some protection)
            val helmet = getItemBySlot(EquipmentSlot.HEAD)
            if (!helmet.isEmpty) {
                // Helmet provides partial protection but gets damaged faster
                if (random.nextFloat() < 0.1f) {
                    helmet.hurtAndBreak(2, this, EquipmentSlot.HEAD)
                }
                // Still take some damage through helmet
                sunburnTicks++
                if (sunburnTicks >= 10) { // Every 0.5 seconds with helmet
                    sunburnTicks = 0
                    hurtServer(serverLevel, damageSources().onFire(), 1f)
                    remainingFireTicks = 40
                }
            } else {
                // No helmet - burn fast!
                sunburnTicks++
                if (sunburnTicks >= 5) { // Every 0.25 seconds without helmet
                    sunburnTicks = 0
                    hurtServer(serverLevel, damageSources().onFire(), 2f)
                    remainingFireTicks = 80 // Longer fire
                }
            }
        } else {
            sunburnTicks = 0
        }
    }
    
    fun isFleeing(): Boolean = isFleeing

    override fun finalizeSpawn(
        accessor: ServerLevelAccessor,
        difficulty: DifficultyInstance,
        reason: EntitySpawnReason,
        spawnData: SpawnGroupData?
    ): SpawnGroupData? {
        val data = super.finalizeSpawn(accessor, difficulty, reason, spawnData)
        equipLoadout(this.random)
        return data
    }

    private fun equipLoadout(random: RandomSource) {
        val sword = ItemStack(Items.COPPER_SWORD)
        if (random.nextFloat() < 0.12f) {
            val enchLevel = if (random.nextBoolean()) 1 else 2
            val enchantLookup = level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
            val fireAspect = enchantLookup.get(Enchantments.FIRE_ASPECT).orElse(null)
            if (fireAspect != null) {
                sword.enchant(fireAspect, enchLevel)
                swordEnchanted = true
            }
        }
        setItemSlot(EquipmentSlot.MAINHAND, sword)

        maybeEquipArmor(random, EquipmentSlot.HEAD, Items.COPPER_HELMET, 0.04f)
        maybeEquipArmor(random, EquipmentSlot.CHEST, Items.COPPER_CHESTPLATE, 0.03f)
        maybeEquipArmor(random, EquipmentSlot.LEGS, Items.COPPER_LEGGINGS, 0.03f)
        maybeEquipArmor(random, EquipmentSlot.FEET, Items.COPPER_BOOTS, 0.05f)
    }

    private fun maybeEquipArmor(random: RandomSource, slot: EquipmentSlot, item: Item, chance: Float) {
        if (random.nextFloat() >= chance) return
        val stack = ItemStack(item)
        if (stack.isDamageableItem) {
            val damage = (stack.maxDamage * 0.75f).toInt()
            stack.damageValue = damage.coerceAtMost(stack.maxDamage - 1)
        }
        setItemSlot(slot, stack)
    }

    override fun dropCustomDeathLoot(serverLevel: ServerLevel, source: DamageSource, hitByPlayer: Boolean) {
        val mainHand = getItemBySlot(EquipmentSlot.MAINHAND)
        if (!mainHand.isEmpty) {
            val dropChance = if (swordEnchanted) 0.4 else 0.6
            if (random.nextDouble() < dropChance) {
                spawnAtLocation(serverLevel, mainHand.copy())
            }
        }

        dropArmorIfPresent(serverLevel, EquipmentSlot.HEAD)
        dropArmorIfPresent(serverLevel, EquipmentSlot.CHEST)
        dropArmorIfPresent(serverLevel, EquipmentSlot.LEGS)
        dropArmorIfPresent(serverLevel, EquipmentSlot.FEET)

        super.dropCustomDeathLoot(serverLevel, source, hitByPlayer)
    }

    private fun dropArmorIfPresent(serverLevel: ServerLevel, slot: EquipmentSlot) {
        val stack = getItemBySlot(slot)
        if (!stack.isEmpty) {
            spawnAtLocation(serverLevel, stack.copy())
            setItemSlot(slot, ItemStack.EMPTY)
        }
    }

    companion object {
        fun createAttributes(): AttributeSupplier.Builder = createMobAttributes()
            .add(Attributes.MAX_HEALTH, 30.0) // Decent health - tougher than zombie
            .add(Attributes.MOVEMENT_SPEED, 0.32) // Fast and aggressive
            .add(Attributes.ATTACK_DAMAGE, 3.5) // Zombie + 0.5 damage (zombie = 3)
            .add(Attributes.FOLLOW_RANGE, 40.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.2) // Slight knockback resistance

        fun canSpawn(
            type: EntityType<CopperStalkerEntity>,
            world: ServerLevelAccessor,
            reason: EntitySpawnReason,
            pos: BlockPos,
            random: RandomSource
        ): Boolean {
            val serverLevel = world as? ServerLevel ?: return false
            
            // Check if extra mobs feature is enabled
            if (!AeonisFeatures.areExtraMobsEnabled(serverLevel)) {
                return false
            }
            
            val time = serverLevel.dayTime % 24000L
            val isNight = time in 13000L..23000L
            return isNight && checkMonsterSpawnRules(type, world, reason, pos, random)
        }
    }
}

/**
 * Flee goal for Copper Stalker - runs away from players when fleeing
 */
private class CopperStalkerFleeGoal(private val stalker: CopperStalkerEntity) : Goal() {
    private var fleeX = 0.0
    private var fleeY = 0.0
    private var fleeZ = 0.0
    
    init {
        flags = EnumSet.of(Flag.MOVE)
    }
    
    override fun canUse(): Boolean {
        return stalker.isFleeing()
    }
    
    override fun canContinueToUse(): Boolean {
        return stalker.isFleeing() && !stalker.navigation.isDone
    }
    
    override fun start() {
        // Find a position away from the nearest player
        val nearestPlayer = stalker.level().getNearestPlayer(stalker, 16.0)
        if (nearestPlayer != null) {
            // Calculate direction away from player
            val dx = stalker.x - nearestPlayer.x
            val dz = stalker.z - nearestPlayer.z
            val dist = kotlin.math.sqrt(dx * dx + dz * dz)
            
            if (dist > 0) {
                // Normalize and scale
                val fleeDistance = 16.0
                fleeX = stalker.x + (dx / dist) * fleeDistance
                fleeZ = stalker.z + (dz / dist) * fleeDistance
                fleeY = stalker.y
                
                // Try to find a valid position
                stalker.navigation.moveTo(fleeX, fleeY, fleeZ, 1.5) // Fast flee speed
            }
        } else {
            // No player nearby, just move randomly away
            val random = stalker.random
            fleeX = stalker.x + (random.nextDouble() - 0.5) * 20
            fleeZ = stalker.z + (random.nextDouble() - 0.5) * 20
            fleeY = stalker.y
            stalker.navigation.moveTo(fleeX, fleeY, fleeZ, 1.5)
        }
    }
    
    override fun tick() {
        // Keep fleeing, re-navigate if stuck
        if (stalker.navigation.isDone && stalker.isFleeing()) {
            start() // Try a new flee direction
        }
    }
    
    override fun stop() {
        stalker.navigation.stop()
    }
}

private class ExtendedReachMeleeGoal(
    private val mob: PathfinderMob,
    speed: Double,
    pauseWhenIdle: Boolean,
    private val reach: Double
) : MeleeAttackGoal(mob, speed, pauseWhenIdle) {

    override fun tick() {
        val target = mob.target
        if (target == null) {
            super.tick()
            return
        }
        
        mob.lookControl.setLookAt(target, 30f, 30f)
        
        val distSq = mob.distanceToSqr(target)
        val reachSq = reach * reach
        
        if (distSq <= reachSq && isTimeToAttack) {
            resetAttackCooldown()
            val serverLevel = mob.level() as? ServerLevel
            if (serverLevel != null) {
                mob.doHurtTarget(serverLevel, target)
            }
        } else {
            super.tick()
        }
    }
}
