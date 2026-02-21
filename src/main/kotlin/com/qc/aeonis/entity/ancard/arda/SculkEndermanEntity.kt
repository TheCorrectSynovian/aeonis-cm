package com.qc.aeonis.entity.ancard.arda

import com.qc.aeonis.entity.ancard.geo.LegacyGeoUtil
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
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
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import java.util.EnumSet
import software.bernie.geckolib.animatable.manager.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.`object`.PlayState
import software.bernie.geckolib.animation.state.AnimationTest

/**
 * Ported from Arda's Sculks.
 * Entity id: aeonis:sculkenderman
 */
class SculkEndermanEntity(type: EntityType<out SculkEndermanEntity>, level: Level) :
    PortedArdaGeoMonster(type, level, "sculkenderman", idleAnim = "ambient", walkAnim = "walk", attackAnim = "attack") {

    private var attackAnimTicks = 0
    private var blinkCooldown = 0
    private var blinkAnimTicks = 0

    companion object {
        fun createAttributes(): AttributeSupplier.Builder = createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 44.0)
            .add(Attributes.MOVEMENT_SPEED, 0.30)
            .add(Attributes.ATTACK_DAMAGE, 9.0)
            .add(Attributes.ARMOR, 4.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.4)
            .add(Attributes.FOLLOW_RANGE, 36.0)
    }

    override fun registerGoals() {
        goalSelector.addGoal(0, FloatGoal(this))
        goalSelector.addGoal(1, SculkEndermanBlinkGoal(this))
        goalSelector.addGoal(2, MeleeAttackGoal(this, 1.2, true))
        goalSelector.addGoal(5, WaterAvoidingRandomStrollGoal(this, 0.8))
        goalSelector.addGoal(6, LookAtPlayerGoal(this, Player::class.java, 16.0f))
        goalSelector.addGoal(7, RandomLookAroundGoal(this))

        targetSelector.addGoal(1, HurtByTargetGoal(this))
        targetSelector.addGoal(2, NearestAttackableTargetGoal(this, Player::class.java, true))
    }

    override fun doHurtTarget(level: net.minecraft.server.level.ServerLevel, target: net.minecraft.world.entity.Entity): Boolean {
        val did = super.doHurtTarget(level, target)
        if (did) {
            attackAnimTicks = 10
            if (target is LivingEntity) {
                target.invulnerableTime = 0
            }
        }
        return did
    }

    override fun aiStep() {
        super.aiStep()
        if (!level().isClientSide) {
            if (attackAnimTicks > 0) attackAnimTicks--
            if (blinkCooldown > 0) blinkCooldown--
            if (blinkAnimTicks > 0) blinkAnimTicks--
        }
    }

    fun canBlinkStrike(): Boolean {
        val t = target ?: return false
        if (blinkCooldown > 0 || !onGround()) return false
        val d = distanceTo(t)
        return d in 4.0..14.0 && hasLineOfSight(t)
    }

    fun blinkStrike() {
        val t = target ?: return
        val look = t.lookAngle.normalize()
        val behind = t.position().subtract(look.scale(2.5))
        teleportTo(behind.x, t.y, behind.z)
        playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0f, 0.65f)
        blinkCooldown = 90
        blinkAnimTicks = 8
        attackAnimTicks = 10
    }

    override fun getAmbientSound(): SoundEvent = SoundEvents.ENDERMAN_AMBIENT
    override fun getHurtSound(source: DamageSource): SoundEvent = SoundEvents.ENDERMAN_HURT
    override fun getDeathSound(): SoundEvent = SoundEvents.ENDERMAN_DEATH

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController<SculkEndermanEntity>("move", 4) { test: AnimationTest<SculkEndermanEntity> ->
                if (blinkAnimTicks > 0) return@AnimationController test.setAndContinue(LegacyGeoUtil.once(attackAnim))
                if (test.isMoving()) test.setAndContinue(LegacyGeoUtil.loop(walkAnim))
                else test.setAndContinue(LegacyGeoUtil.loop(idleAnim))
            }
        )
        controllers.add(
            AnimationController<SculkEndermanEntity>("attack", 0) { test: AnimationTest<SculkEndermanEntity> ->
                if (attackAnimTicks > 0) return@AnimationController test.setAndContinue(LegacyGeoUtil.once(attackAnim))
                PlayState.STOP
            }
        )
    }
}

private class SculkEndermanBlinkGoal(private val mob: SculkEndermanEntity) : Goal() {
    init {
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK))
    }

    override fun canUse(): Boolean = mob.canBlinkStrike()

    override fun start() {
        mob.blinkStrike()
    }

    override fun canContinueToUse(): Boolean = false
}
