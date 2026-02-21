package com.qc.aeonis.entity.ancard.arda

import com.qc.aeonis.entity.ancard.geo.LegacyGeoUtil
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.InteractionHand
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal
import net.minecraft.world.entity.monster.RangedAttackMob
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import software.bernie.geckolib.animatable.manager.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.`object`.PlayState
import software.bernie.geckolib.animation.state.AnimationTest

/**
 * Ported from Arda's Sculks.
 * Entity id: aeonis:sculkskeleton
 */
class SculkSkeletonEntity(type: EntityType<out SculkSkeletonEntity>, level: Level) :
    PortedArdaGeoMonster(type, level, "sculkskeleton", idleAnim = "ambient", walkAnim = "yurume", attackAnim = "yay"), RangedAttackMob {

    private var attackAnimTicks = 0

    init {
        setItemInHand(InteractionHand.MAIN_HAND, Items.BOW.defaultInstance)
    }

    companion object {
        fun createAttributes(): AttributeSupplier.Builder = createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 26.0)
            .add(Attributes.MOVEMENT_SPEED, 0.25)
            .add(Attributes.ATTACK_DAMAGE, 6.0)
            .add(Attributes.ARMOR, 3.0)
            .add(Attributes.FOLLOW_RANGE, 32.0)
    }

    override fun registerGoals() {
        goalSelector.addGoal(0, FloatGoal(this))
        goalSelector.addGoal(2, RangedBowAttackGoal(this, 1.05, 25, 16.0f))
        goalSelector.addGoal(3, MeleeAttackGoal(this, 1.15, true))
        goalSelector.addGoal(5, WaterAvoidingRandomStrollGoal(this, 0.75))
        goalSelector.addGoal(6, LookAtPlayerGoal(this, Player::class.java, 14.0f))
        goalSelector.addGoal(7, RandomLookAroundGoal(this))

        targetSelector.addGoal(1, HurtByTargetGoal(this))
        targetSelector.addGoal(2, NearestAttackableTargetGoal(this, Player::class.java, true))
    }

    override fun doHurtTarget(level: net.minecraft.server.level.ServerLevel, target: net.minecraft.world.entity.Entity): Boolean {
        val did = super.doHurtTarget(level, target)
        if (did) attackAnimTicks = 10
        return did
    }

    override fun performRangedAttack(target: LivingEntity, distanceFactor: Float) {
        if (mainHandItem.item !is BowItem) {
            setItemInHand(InteractionHand.MAIN_HAND, Items.BOW.defaultInstance)
        }
        val arrow = ProjectileUtil.getMobArrow(this, mainHandItem, distanceFactor, Items.ARROW.defaultInstance)
        val dx = target.x - x
        val dz = target.z - z
        val horizontal = kotlin.math.sqrt(dx * dx + dz * dz)
        val dy = target.getY(0.3333333333333333) - arrow.y + horizontal * 0.2
        arrow.shoot(dx, dy, dz, 1.6f, (14 - level().difficulty.id * 4).toFloat())
        playSound(SoundEvents.SKELETON_SHOOT, 1.0f, 1.0f / (random.nextFloat() * 0.4f + 0.8f))
        level().addFreshEntity(arrow)
        attackAnimTicks = 12
    }

    override fun aiStep() {
        super.aiStep()
        if (!level().isClientSide && attackAnimTicks > 0) attackAnimTicks--
    }

    override fun getAmbientSound(): SoundEvent = SoundEvents.SKELETON_AMBIENT
    override fun getHurtSound(source: DamageSource): SoundEvent = SoundEvents.SKELETON_HURT
    override fun getDeathSound(): SoundEvent = SoundEvents.SKELETON_DEATH

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController<SculkSkeletonEntity>("move", 4) { test: AnimationTest<SculkSkeletonEntity> ->
                if (test.isMoving()) test.setAndContinue(LegacyGeoUtil.loop(walkAnim))
                else test.setAndContinue(LegacyGeoUtil.loop(idleAnim))
            }
        )
        controllers.add(
            AnimationController<SculkSkeletonEntity>("attack", 0) { test: AnimationTest<SculkSkeletonEntity> ->
                if (attackAnimTicks > 0) return@AnimationController test.setAndContinue(LegacyGeoUtil.once(attackAnim))
                PlayState.STOP
            }
        )
    }
}
