package com.qc.aeonis.entity.ancard

import com.qc.aeonis.entity.ancard.geo.AncardGeoMonster
import com.qc.aeonis.entity.ancard.geo.AncardGeoUtil
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.control.FlyingMoveControl
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal
import net.minecraft.world.entity.ai.goal.RangedAttackGoal
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation
import net.minecraft.world.entity.ai.navigation.PathNavigation
import net.minecraft.world.entity.monster.RangedAttackMob
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import software.bernie.geckolib.animatable.manager.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.state.AnimationTest
import software.bernie.geckolib.animation.`object`.PlayState

/**
 * ancard:obelisk_sentinel
 * Floating stone/obsidian construct with rotating shards.
 *
 * Ability: ranged slow homing shard projectile.
 */
class ObeliskSentinelEntity(type: EntityType<out ObeliskSentinelEntity>, level: Level) :
    AncardGeoMonster(type, level, "obelisk_sentinel"), RangedAttackMob {

    private var attackAnimTicks = 0

    init {
        moveControl = FlyingMoveControl(this, 20, true)
    }

    companion object {
        fun createAttributes(): AttributeSupplier.Builder = createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 42.0)
            .add(Attributes.MOVEMENT_SPEED, 0.18)
            .add(Attributes.FLYING_SPEED, 0.36)
            .add(Attributes.ARMOR, 10.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.85)
            .add(Attributes.FOLLOW_RANGE, 40.0)
            .add(Attributes.ATTACK_DAMAGE, 4.0)
    }

    override fun registerGoals() {
        goalSelector.addGoal(0, FloatGoal(this))
        goalSelector.addGoal(2, RangedAttackGoal(this, 1.0, 30, 50, 22.0f))
        goalSelector.addGoal(5, WaterAvoidingRandomFlyingGoal(this, 0.9))
        goalSelector.addGoal(6, LookAtPlayerGoal(this, Player::class.java, 20.0f))
        goalSelector.addGoal(7, RandomLookAroundGoal(this))

        targetSelector.addGoal(1, HurtByTargetGoal(this))
        targetSelector.addGoal(2, NearestAttackableTargetGoal(this, Player::class.java, true))
    }

    override fun createNavigation(level: Level): PathNavigation = FlyingPathNavigation(this, level)

    override fun aiStep() {
        super.aiStep()
        if (!level().isClientSide) {
            if (attackAnimTicks > 0) attackAnimTicks--
            setNoGravity(true)
        }
    }

    override fun performRangedAttack(target: LivingEntity, distanceFactor: Float) {
        if (level().isClientSide) return
        val shard = ObeliskShardProjectileEntity(AncardEntities.OBELISK_SHARD, this, level())
        shard.setPos(x, eyeY - 0.1, z)

        val to = target.eyePosition.subtract(shard.position())
        val dir = to.normalize()
        shard.shoot(dir.x, dir.y, dir.z, 0.7f, 8.0f)
        level().addFreshEntity(shard)
        playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 0.7f)
        attackAnimTicks = 12
    }

    override fun getAmbientSound(): SoundEvent = SoundEvents.AMETHYST_CLUSTER_HIT
    override fun getHurtSound(source: DamageSource): SoundEvent = SoundEvents.DEEPSLATE_HIT
    override fun getDeathSound(): SoundEvent = SoundEvents.DEEPSLATE_BREAK

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController<ObeliskSentinelEntity>("move", 3) { test: AnimationTest<ObeliskSentinelEntity> ->
                if (test.isMoving()) test.setAndContinue(AncardGeoUtil.loop(animId, "walk"))
                else test.setAndContinue(AncardGeoUtil.loop(animId, "idle"))
            }
        )
        controllers.add(
            AnimationController<ObeliskSentinelEntity>("attack", 0) { test: AnimationTest<ObeliskSentinelEntity> ->
                if (attackAnimTicks > 0) {
                    return@AnimationController test.setAndContinue(AncardGeoUtil.once(animId, "attack"))
                }
                PlayState.STOP
            }
        )
    }
}
