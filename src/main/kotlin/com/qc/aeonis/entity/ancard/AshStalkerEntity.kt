package com.qc.aeonis.entity.ancard

import com.qc.aeonis.entity.ancard.geo.AncardGeoMonster
import com.qc.aeonis.entity.ancard.geo.AncardGeoUtil
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.*
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import software.bernie.geckolib.animatable.manager.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.`object`.PlayState

/**
 * Ash Stalker - spawns in the Ancard Ash Barrens biome.
 * 
 * Behavior:
 * - Resistant to fire and lava
 * - Can "burrow" into the ground (brief invisibility + teleport)
 * - Ambush predator: stays still until player is close, then strikes
 * - Tough, slow-moving melee attacker
 */
class AshStalkerEntity(type: EntityType<out AshStalkerEntity>, level: Level) :
    AncardGeoMonster(type, level, "ash_stalker") {

    private var burrowCooldown = 0
    private var isBurrowed = false
    private var burrowTicks = 0
    private var attackAnimTicks = 0

    companion object {
        private const val BURROW_COOLDOWN_MAX = 200 // 10 seconds
        private const val BURROW_DURATION = 40 // 2 seconds underground
        private const val BURROW_RANGE = 8.0

        fun createAttributes(): AttributeSupplier.Builder = createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 40.0)
            .add(Attributes.MOVEMENT_SPEED, 0.23)
            .add(Attributes.ATTACK_DAMAGE, 8.0)
            .add(Attributes.ARMOR, 6.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.4)
            .add(Attributes.FOLLOW_RANGE, 32.0)
    }

    override fun registerGoals() {
        goalSelector.addGoal(0, FloatGoal(this))
        goalSelector.addGoal(1, AshStalkerBurrowGoal(this))
        goalSelector.addGoal(2, MeleeAttackGoal(this, 1.0, true))
        goalSelector.addGoal(5, WaterAvoidingRandomStrollGoal(this, 0.8))
        goalSelector.addGoal(6, LookAtPlayerGoal(this, Player::class.java, 16.0f))
        goalSelector.addGoal(7, RandomLookAroundGoal(this))

        targetSelector.addGoal(1, HurtByTargetGoal(this))
        targetSelector.addGoal(2, NearestAttackableTargetGoal(this, Player::class.java, true))
    }

    override fun aiStep() {
        super.aiStep()
        if (!level().isClientSide) {
            if (burrowCooldown > 0) burrowCooldown--
            if (attackAnimTicks > 0) attackAnimTicks--

            if (isBurrowed) {
                burrowTicks--
                // While burrowed, entity is invisible and invulnerable
                if (!isInvisible) isInvisible = true
                noPhysics = true

                if (burrowTicks <= 0) {
                    // Emerge from burrow - teleport near target
                    isBurrowed = false
                    isInvisible = false
                    noPhysics = false
                    burrowCooldown = BURROW_COOLDOWN_MAX

                    val t = target
                    if (t != null && t.isAlive) {
                        // Teleport behind the target
                        val look = t.lookAngle.normalize()
                        val behindPos = t.position().subtract(look.scale(2.0))
                        teleportTo(behindPos.x, behindPos.y, behindPos.z)
                        playSound(SoundEvents.ENDERMAN_TELEPORT, 0.8f, 0.6f)
                    }
                }
            }
        }
    }

    fun canBurrow(): Boolean = !isBurrowed && burrowCooldown <= 0 && target != null && onGround()

    fun startBurrow() {
        isBurrowed = true
        burrowTicks = BURROW_DURATION
        playSound(SoundEvents.GRAVEL_BREAK, 1.2f, 0.5f)
    }

    override fun isOnFire(): Boolean = false // Fire resistant visually

    override fun fireImmune(): Boolean = true

    override fun isSensitiveToWater(): Boolean = false

    override fun getAmbientSound() = SoundEvents.BASALT_STEP

    override fun getHurtSound(source: DamageSource) = SoundEvents.IRON_GOLEM_HURT

    override fun getDeathSound() = SoundEvents.IRON_GOLEM_DEATH

    override fun doHurtTarget(serverLevel: ServerLevel, target: net.minecraft.world.entity.Entity): Boolean {
        val did = super.doHurtTarget(serverLevel, target)
        if (did) attackAnimTicks = 10
        return did
    }

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController<AshStalkerEntity>("move", 3) { test ->
                if (isBurrowed) test.setAndContinue(AncardGeoUtil.once(animId, "burrow"))
                else if (test.isMoving()) test.setAndContinue(AncardGeoUtil.loop(animId, "walk"))
                else test.setAndContinue(AncardGeoUtil.loop(animId, "idle"))
            }
        )
        controllers.add(
            AnimationController<AshStalkerEntity>("attack", 0) { test ->
                if (attackAnimTicks > 0) test.setAndContinue(AncardGeoUtil.once(animId, "attack"))
                else PlayState.STOP
            }
        )
    }
}

/**
 * Custom burrow AI goal for the Ash Stalker.
 */
class AshStalkerBurrowGoal(private val stalker: AshStalkerEntity) : Goal() {
    override fun canUse(): Boolean = stalker.canBurrow()

    override fun start() {
        stalker.startBurrow()
    }

    override fun canContinueToUse(): Boolean = false // One-shot goal
}
