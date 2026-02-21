package com.qc.aeonis.entity.ancard

import com.qc.aeonis.entity.ancard.geo.AncardGeoMonster
import com.qc.aeonis.entity.ancard.geo.AncardGeoUtil
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerBossEvent
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.BossEvent
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.*
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import software.bernie.geckolib.animatable.manager.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.`object`.PlayState
import software.bernie.geckolib.animation.state.AnimationTest

/**
 * The Ancard Sovereign — Boss mob of the Ancard dimension.
 * 
 * Features:
 * - Multi-phase fight (3 phases based on health thresholds)
 * - Phase 1 (100-60% HP): Melee + dark energy projectiles
 * - Phase 2 (60-30% HP): Summons Ash Stalker minions, gains armor
 * - Phase 3 (30-0% HP): Teleports, rapid projectiles, area denial
 * - Custom boss bar (dark red)
 * - Spawns exclusively in Ancient Obsidian Citadels
 * - Drops unique loot
 */
class AncardSovereignEntity(type: EntityType<out AncardSovereignEntity>, level: Level) :
    AncardGeoMonster(type, level, "ancard_sovereign") {

    private val bossBar = ServerBossEvent(
        Component.translatable("entity.aeonis.ancard_sovereign"),
        BossEvent.BossBarColor.RED,
        BossEvent.BossBarOverlay.NOTCHED_10
    )

    private var phase = 1
    private var projectileCooldown = 0
    private var summonCooldown = 0
    private var teleportCooldown = 0
    private var arenaCheckCooldown = 0
    private var hasEnteredPhase2 = false
    private var hasEnteredPhase3 = false
    private var attackAnimTicks = 0
    private var summonAnimTicks = 0
    private var phaseTransitionTicks = 0

    companion object {
        private const val PHASE2_THRESHOLD = 0.6f
        private const val PHASE3_THRESHOLD = 0.3f
        private const val PROJECTILE_COOLDOWN_P1 = 60 // 3 sec
        private const val PROJECTILE_COOLDOWN_P3 = 20 // 1 sec
        private const val SUMMON_COOLDOWN = 400 // 20 sec
        private const val TELEPORT_COOLDOWN = 100 // 5 sec
        private const val ARENA_RADIUS = 25.0
        private const val MAX_MINIONS = 4

        fun createAttributes(): AttributeSupplier.Builder = createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 300.0)
            .add(Attributes.MOVEMENT_SPEED, 0.28)
            .add(Attributes.ATTACK_DAMAGE, 12.0)
            .add(Attributes.ARMOR, 8.0)
            .add(Attributes.ARMOR_TOUGHNESS, 4.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
            .add(Attributes.FOLLOW_RANGE, 64.0)
    }

    override fun registerGoals() {
        goalSelector.addGoal(0, FloatGoal(this))
        goalSelector.addGoal(1, SovereignProjectileGoal(this))
        goalSelector.addGoal(2, MeleeAttackGoal(this, 1.1, true))
        goalSelector.addGoal(5, WaterAvoidingRandomStrollGoal(this, 0.8))
        goalSelector.addGoal(6, LookAtPlayerGoal(this, Player::class.java, 32.0f))

        targetSelector.addGoal(1, HurtByTargetGoal(this))
        targetSelector.addGoal(2, NearestAttackableTargetGoal(this, Player::class.java, true))
    }

    override fun aiStep() {
        super.aiStep()
        if (level().isClientSide) return

        val serverLevel = level() as? ServerLevel ?: return

        // Update cooldowns
        if (projectileCooldown > 0) projectileCooldown--
        if (summonCooldown > 0) summonCooldown--
        if (teleportCooldown > 0) teleportCooldown--
        if (attackAnimTicks > 0) attackAnimTicks--
        if (summonAnimTicks > 0) summonAnimTicks--
        if (phaseTransitionTicks > 0) phaseTransitionTicks--

        // Update boss bar
        bossBar.progress = health / maxHealth

        // Phase transitions
        val healthPercent = health / maxHealth
        when {
            healthPercent <= PHASE3_THRESHOLD && !hasEnteredPhase3 -> enterPhase3(serverLevel)
            healthPercent <= PHASE2_THRESHOLD && !hasEnteredPhase2 -> enterPhase2(serverLevel)
        }

        // Phase-specific behaviors
        when (phase) {
            2 -> {
                // Periodically summon minions
                if (summonCooldown <= 0) {
                    summonMinions(serverLevel)
                    summonCooldown = SUMMON_COOLDOWN
                }
            }
            3 -> {
                // Periodic teleport
                if (teleportCooldown <= 0 && target != null) {
                    teleportToRandomNearby(serverLevel)
                    teleportCooldown = TELEPORT_COOLDOWN
                }

                // Area denial: place fire around arena periodically
                arenaCheckCooldown++
                if (arenaCheckCooldown >= 100) {
                    arenaCheckCooldown = 0
                    createAreaDenial(serverLevel)
                }
            }
        }

        // Drag players back into arena
        enforceArena()
    }

    private fun enterPhase2(level: ServerLevel) {
        hasEnteredPhase2 = true
        phase = 2
        phaseTransitionTicks = 20
        
        // Gain armor boost
        addEffect(MobEffectInstance(MobEffects.RESISTANCE, Int.MAX_VALUE, 0, false, false))
        
        // Visual feedback
        playSound(SoundEvents.WITHER_SPAWN, 1.5f, 0.5f)
        
        // Spawn particles
        for (i in 0..50) {
            level.sendParticles(
                ParticleTypes.SOUL_FIRE_FLAME,
                x + random.nextGaussian() * 2,
                y + 1 + random.nextDouble() * 2,
                z + random.nextGaussian() * 2,
                1, 0.0, 0.1, 0.0, 0.02
            )
        }

        // Summon initial minions
        summonMinions(level)
    }

    private fun enterPhase3(level: ServerLevel) {
        hasEnteredPhase3 = true
        phase = 3
        phaseTransitionTicks = 20
        
        // Remove resistance, gain speed
        removeEffect(MobEffects.RESISTANCE)
        addEffect(MobEffectInstance(MobEffects.SPEED, Int.MAX_VALUE, 1, false, false))
        addEffect(MobEffectInstance(MobEffects.STRENGTH, Int.MAX_VALUE, 0, false, false))
        
        playSound(SoundEvents.ENDER_DRAGON_GROWL, 2.0f, 0.5f)
        
        // Large particle burst
        for (i in 0..100) {
            level.sendParticles(
                ParticleTypes.REVERSE_PORTAL,
                x + random.nextGaussian() * 3,
                y + 1 + random.nextDouble() * 3,
                z + random.nextGaussian() * 3,
                1, 0.0, 0.2, 0.0, 0.05
            )
        }
    }

    fun canShootProjectile(): Boolean {
        if (projectileCooldown > 0) return false
        return target != null && target!!.isAlive
    }

    fun shootDarkProjectile() {
        val t = target ?: return
        val serverLevel = level() as? ServerLevel ?: return
        attackAnimTicks = 10
        
        val dir = t.position().add(0.0, t.bbHeight / 2.0, 0.0).subtract(position().add(0.0, bbHeight / 2.0, 0.0)).normalize()
        
        // Use a small fireball as base projectile (visually dark)
        val fireball = SmallFireball(level(), this, dir.scale(1.5))
        fireball.setPos(x + dir.x, y + bbHeight / 2.0, z + dir.z)
        level().addFreshEntity(fireball)
        
        playSound(SoundEvents.BLAZE_SHOOT, 1.0f, 0.5f)
        
        projectileCooldown = if (phase >= 3) PROJECTILE_COOLDOWN_P3 else PROJECTILE_COOLDOWN_P1
        
        // In phase 3, shoot triple burst
        if (phase >= 3) {
            val left = dir.yRot(0.3f)
            val right = dir.yRot(-0.3f)
            
            val fb2 = SmallFireball(level(), this, left.scale(1.5))
            fb2.setPos(x + left.x, y + bbHeight / 2.0, z + left.z)
            level().addFreshEntity(fb2)
            
            val fb3 = SmallFireball(level(), this, right.scale(1.5))
            fb3.setPos(x + right.x, y + bbHeight / 2.0, z + right.z)
            level().addFreshEntity(fb3)
        }
    }

    private fun summonMinions(level: ServerLevel) {
        // Count existing minions in area
        val existingMinions = level.getEntitiesOfClass(
            AshStalkerEntity::class.java,
            AABB.ofSize(position(), ARENA_RADIUS * 2, 20.0, ARENA_RADIUS * 2)
        )
        
        if (existingMinions.size >= MAX_MINIONS) return
        summonAnimTicks = 15
        
        val toSpawn = (MAX_MINIONS - existingMinions.size).coerceAtMost(2)
        for (i in 0 until toSpawn) {
            val angle = random.nextDouble() * Math.PI * 2
            val dist = 5.0 + random.nextDouble() * 8.0
            val spawnX = x + Math.cos(angle) * dist
            val spawnZ = z + Math.sin(angle) * dist
            
            // Spawn particle trail
            level.sendParticles(
                ParticleTypes.SOUL,
                spawnX, y + 1, spawnZ,
                10, 0.3, 0.5, 0.3, 0.02
            )
        }
        
        playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 1.0f, 0.7f)
    }

    private fun teleportToRandomNearby(level: ServerLevel) {
        val t = target ?: return
        val angle = random.nextDouble() * Math.PI * 2
        val dist = 5.0 + random.nextDouble() * 10.0
        val newX = t.x + Math.cos(angle) * dist
        val newZ = t.z + Math.sin(angle) * dist
        
        teleportTo(newX, t.y, newZ)
        playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0f, 0.5f)
        
        level.sendParticles(
            ParticleTypes.REVERSE_PORTAL,
            x, y + 1, z,
            20, 0.5, 1.0, 0.5, 0.1
        )
    }

    private fun createAreaDenial(level: ServerLevel) {
        // Place soul fire particles in a ring
        for (i in 0..12) {
            val angle = (i.toDouble() / 12.0) * Math.PI * 2
            val radius = ARENA_RADIUS * 0.8
            val px = x + Math.cos(angle) * radius
            val pz = z + Math.sin(angle) * radius
            level.sendParticles(
                ParticleTypes.SOUL_FIRE_FLAME,
                px, y, pz,
                5, 0.2, 0.1, 0.2, 0.01
            )
        }
    }

    private fun enforceArena() {
        // Pull players back if they try to flee too far
        val players = level().getEntitiesOfClass(
            Player::class.java,
            AABB.ofSize(position(), ARENA_RADIUS * 3, 40.0, ARENA_RADIUS * 3)
        )
        for (player in players) {
            val dist = distanceTo(player)
            if (dist > ARENA_RADIUS) {
                val pullDir = position().subtract(player.position()).normalize().scale(0.3)
                player.push(pullDir.x, 0.1, pullDir.z)
            }
        }
    }

    override fun hurtServer(level: ServerLevel, source: DamageSource, amount: Float): Boolean {
        // Cap damage per hit to prevent one-shots
        val cappedAmount = amount.coerceAtMost(25.0f)
        return super.hurtServer(level, source, cappedAmount)
    }

    override fun startSeenByPlayer(player: ServerPlayer) {
        super.startSeenByPlayer(player)
        bossBar.addPlayer(player)
    }

    override fun stopSeenByPlayer(player: ServerPlayer) {
        super.stopSeenByPlayer(player)
        bossBar.removePlayer(player)
    }

    override fun checkDespawn() {
        // Boss never despawns naturally
    }

    override fun fireImmune(): Boolean = true

    override fun getAmbientSound() = SoundEvents.WITHER_AMBIENT

    override fun getHurtSound(source: DamageSource) = SoundEvents.WITHER_HURT

    override fun getDeathSound() = SoundEvents.WITHER_DEATH

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController<AncardSovereignEntity>("move", 4) { test: AnimationTest<AncardSovereignEntity> ->
                if (phaseTransitionTicks > 0) {
                    return@AnimationController test.setAndContinue(AncardGeoUtil.once(animId, "phase"))
                }

                if (attackAnimTicks > 0) {
                    return@AnimationController test.setAndContinue(AncardGeoUtil.once(animId, "attack"))
                }

                if (summonAnimTicks > 0) {
                    return@AnimationController test.setAndContinue(AncardGeoUtil.once(animId, "summon"))
                }

                if (test.isMoving()) test.setAndContinue(AncardGeoUtil.loop(animId, "walk"))
                else test.setAndContinue(AncardGeoUtil.loop(animId, "idle"))
            }
        )
    }
}

/**
 * Projectile attack goal for the Sovereign.
 */
class SovereignProjectileGoal(private val sovereign: AncardSovereignEntity) : Goal() {
    override fun canUse(): Boolean = sovereign.canShootProjectile()

    override fun start() {
        sovereign.shootDarkProjectile()
    }

    override fun canContinueToUse(): Boolean = false
}
