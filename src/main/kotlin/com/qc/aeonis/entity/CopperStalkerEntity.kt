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
import net.minecraft.world.level.ServerLevelAccessor

class CopperStalkerEntity(entityType: EntityType<out CopperStalkerEntity>, level: Level) : Monster(entityType, level) {
    private val lowHealthThreshold = 10f
    private val invisIntervalTicks = 200
    private val invisDurationTicks = 40
    private var swordEnchanted = false

    override fun registerGoals() {
        goalSelector.addGoal(0, FloatGoal(this))
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
        if (!level().isClientSide && health <= lowHealthThreshold) {
            if (tickCount % invisIntervalTicks == 0) {
                addEffect(MobEffectInstance(MobEffects.INVISIBILITY, invisDurationTicks, 0, false, true))
            }
        }
    }

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
            .add(Attributes.MAX_HEALTH, 100.0)
            .add(Attributes.MOVEMENT_SPEED, 0.28)
            .add(Attributes.ATTACK_DAMAGE, 4.0)
            .add(Attributes.FOLLOW_RANGE, 40.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.15)

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
