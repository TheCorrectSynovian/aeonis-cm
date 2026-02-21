package com.qc.aeonis.entity.ancard.arda

import com.qc.aeonis.entity.ancard.geo.LegacyGeoUtil
import net.minecraft.core.particles.ParticleTypes
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
import net.minecraft.world.phys.Vec3
import java.util.EnumSet
import software.bernie.geckolib.animatable.manager.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.`object`.PlayState
import software.bernie.geckolib.animation.state.AnimationTest

/**
 * Ported from Arda's Sculks.
 * Entity id: aeonis:sculkgolembossreloaded
 */
class SculkGolemBossReloadedEntity(type: EntityType<out SculkGolemBossReloadedEntity>, level: Level) :
    PortedArdaGeoMonster(type, level, "sculkgolembossreloaded", idleAnim = "yasam", walkAnim = "yurume", attackAnim = "saldırınormal") {

    private var attackAnimTicks = 0
    private var stompCooldown = 0

    companion object {
        fun createAttributes(): AttributeSupplier.Builder = createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 110.0)
            .add(Attributes.MOVEMENT_SPEED, 0.18)
            .add(Attributes.ATTACK_DAMAGE, 16.0)
            .add(Attributes.ARMOR, 14.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
            .add(Attributes.FOLLOW_RANGE, 44.0)
    }

    override fun registerGoals() {
        goalSelector.addGoal(0, FloatGoal(this))
        goalSelector.addGoal(1, SculkGolemStompGoal(this))
        goalSelector.addGoal(2, MeleeAttackGoal(this, 0.95, true))
        goalSelector.addGoal(5, WaterAvoidingRandomStrollGoal(this, 0.5))
        goalSelector.addGoal(6, LookAtPlayerGoal(this, Player::class.java, 18.0f))
        goalSelector.addGoal(7, RandomLookAroundGoal(this))

        targetSelector.addGoal(1, HurtByTargetGoal(this))
        targetSelector.addGoal(2, NearestAttackableTargetGoal(this, Player::class.java, true))
    }

    override fun doHurtTarget(level: net.minecraft.server.level.ServerLevel, target: net.minecraft.world.entity.Entity): Boolean {
        val did = super.doHurtTarget(level, target)
        if (did) attackAnimTicks = 16
        return did
    }

    override fun aiStep() {
        super.aiStep()
        if (!level().isClientSide) {
            if (attackAnimTicks > 0) attackAnimTicks--
            if (stompCooldown > 0) stompCooldown--
        }
    }

    fun canStomp(): Boolean {
        val t = target ?: return false
        return stompCooldown <= 0 && distanceToSqr(t) <= 6.0 * 6.0
    }

    fun stomp() {
        stompCooldown = 110
        attackAnimTicks = 16

        val victims = level().getEntitiesOfClass(LivingEntity::class.java, boundingBox.inflate(4.5))
        for (victim in victims) {
            if (victim === this) continue
            val dir = Vec3(victim.x - x, 0.0, victim.z - z).normalize().scale(0.85)
            victim.push(dir.x, 0.28, dir.z)
            victim.hurt(damageSources().mobAttack(this), 7.0f)
        }
        (level() as? net.minecraft.server.level.ServerLevel)?.sendParticles(
            ParticleTypes.ASH,
            x,
            y + 0.2,
            z,
            25,
            1.2,
            0.1,
            1.2,
            0.05
        )
        playSound(SoundEvents.ANVIL_LAND, 1.0f, 0.55f)
    }

    override fun getAmbientSound(): SoundEvent = SoundEvents.IRON_GOLEM_REPAIR
    override fun getHurtSound(source: DamageSource): SoundEvent = SoundEvents.IRON_GOLEM_HURT
    override fun getDeathSound(): SoundEvent = SoundEvents.IRON_GOLEM_DEATH

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController<SculkGolemBossReloadedEntity>("move", 5) { test: AnimationTest<SculkGolemBossReloadedEntity> ->
                if (test.isMoving()) test.setAndContinue(LegacyGeoUtil.loop(walkAnim))
                else test.setAndContinue(LegacyGeoUtil.loop(idleAnim))
            }
        )
        controllers.add(
            AnimationController<SculkGolemBossReloadedEntity>("attack", 0) { test: AnimationTest<SculkGolemBossReloadedEntity> ->
                if (attackAnimTicks > 0) return@AnimationController test.setAndContinue(LegacyGeoUtil.once(attackAnim))
                PlayState.STOP
            }
        )
    }
}

private class SculkGolemStompGoal(private val golem: SculkGolemBossReloadedEntity) : Goal() {
    init {
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK))
    }

    override fun canUse(): Boolean = golem.canStomp()

    override fun start() {
        golem.stomp()
    }

    override fun canContinueToUse(): Boolean = false
}
