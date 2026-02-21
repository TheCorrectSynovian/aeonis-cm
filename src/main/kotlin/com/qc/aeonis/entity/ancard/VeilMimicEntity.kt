package com.qc.aeonis.entity.ancard

import com.qc.aeonis.entity.ancard.geo.AncardGeoMonster
import com.qc.aeonis.entity.ancard.geo.AncardGeoUtil
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal
import net.minecraft.world.entity.player.Player
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import software.bernie.geckolib.animatable.manager.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.state.AnimationTest
import software.bernie.geckolib.animation.`object`.PlayState
import java.util.EnumSet

/**
 * ancard:veil_mimic
 * Looks like a decorative ruin prop until a player comes close.
 *
 * Ability: ambush melee + grappling bite (pull target slightly).
 */
class VeilMimicEntity(type: EntityType<out VeilMimicEntity>, level: Level) :
    AncardGeoMonster(type, level, "veil_mimic") {

    private var attackAnimTicks = 0
    private var activateAnimTicks = 0

    companion object {
        private val ACTIVE: EntityDataAccessor<Boolean> =
            SynchedEntityData.defineId(VeilMimicEntity::class.java, EntityDataSerializers.BOOLEAN)

        fun createAttributes(): AttributeSupplier.Builder = createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 24.0)
            .add(Attributes.MOVEMENT_SPEED, 0.24)
            .add(Attributes.ATTACK_DAMAGE, 7.5)
            .add(Attributes.ARMOR, 6.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
            .add(Attributes.FOLLOW_RANGE, 22.0)
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        super.defineSynchedData(builder)
        builder.define(ACTIVE, false)
    }

    fun isActiveMimic(): Boolean = entityData.get(ACTIVE)
    private fun setActiveMimic(active: Boolean) = entityData.set(ACTIVE, active)

    override fun registerGoals() {
        goalSelector.addGoal(0, DormantAmbushGoal(this))
        goalSelector.addGoal(2, MeleeAttackGoal(this, 1.15, true))
        goalSelector.addGoal(5, WaterAvoidingRandomStrollGoal(this, 0.9))
        goalSelector.addGoal(6, LookAtPlayerGoal(this, Player::class.java, 14.0f))
        goalSelector.addGoal(7, RandomLookAroundGoal(this))

        targetSelector.addGoal(1, HurtByTargetGoal(this))
        targetSelector.addGoal(2, NearestAttackableTargetGoal(this, Player::class.java, true))
    }

    override fun aiStep() {
        super.aiStep()
        if (!level().isClientSide) {
            if (attackAnimTicks > 0) attackAnimTicks--
            if (activateAnimTicks > 0) activateAnimTicks--
            if (!isActiveMimic()) {
                // Statue mode: don't keep aggro.
                target = null
                navigation.stop()
                deltaMovement = Vec3.ZERO
            }
        }
    }

    override fun doHurtTarget(serverLevel: ServerLevel, target: Entity): Boolean {
        val did = super.doHurtTarget(serverLevel, target)
        if (did && target is LivingEntity) {
            // Grappling bite: pull target slightly towards the mimic.
            val pull = Vec3(x - target.x, 0.0, z - target.z)
                .normalize()
                .scale(0.35)
            target.push(pull.x, 0.05, pull.z)
            playSound(SoundEvents.CHEST_OPEN, 0.8f, 0.6f)
            attackAnimTicks = 12
        }
        return did
    }

    fun activateAgainst(player: Player) {
        if (!isActiveMimic()) {
            setActiveMimic(true)
            activateAnimTicks = 18
            playSound(SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.7f, 1.2f)
        }
        target = player
    }

    override fun getAmbientSound(): SoundEvent = SoundEvents.DEEPSLATE_BRICKS_HIT
    override fun getHurtSound(source: DamageSource): SoundEvent = SoundEvents.DEEPSLATE_HIT
    override fun getDeathSound(): SoundEvent = SoundEvents.DEEPSLATE_BREAK

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController<VeilMimicEntity>("move", 3) { test: AnimationTest<VeilMimicEntity> ->
                val isActive = isActiveMimic()
                when {
                    activateAnimTicks > 0 -> test.setAndContinue(AncardGeoUtil.once(animId, "activate"))
                    !isActive -> test.setAndContinue(AncardGeoUtil.loop(animId, "idle_statue"))
                    test.isMoving() -> test.setAndContinue(AncardGeoUtil.loop(animId, "walk"))
                    else -> test.setAndContinue(AncardGeoUtil.loop(animId, "idle"))
                }
            }
        )
        controllers.add(
            AnimationController<VeilMimicEntity>("attack", 0) { test: AnimationTest<VeilMimicEntity> ->
                if (attackAnimTicks > 0) {
                    return@AnimationController test.setAndContinue(AncardGeoUtil.once(animId, "attack"))
                }
                PlayState.STOP
            }
        )
    }
}

private class DormantAmbushGoal(private val mimic: VeilMimicEntity) : Goal() {
    init {
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK))
    }

    override fun canUse(): Boolean = !mimic.isActiveMimic()

    override fun tick() {
        mimic.navigation.stop()
        val level = mimic.level()
        val player = level.getNearestPlayer(mimic, 4.0)
        if (player != null && player.isAlive && mimic.hasLineOfSight(player)) {
            mimic.activateAgainst(player)
        }
    }
}
