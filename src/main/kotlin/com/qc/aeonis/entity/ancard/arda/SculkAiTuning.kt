package com.qc.aeonis.entity.ancard.arda

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player

/**
 * Shared combat tuning helpers for Ancard/Arda sculk mobs.
 */
object SculkAiTuning {
    fun isHostileTarget(owner: LivingEntity, target: LivingEntity?): Boolean {
        if (target == null || !target.isAlive || target === owner) return false
        if (owner.isAlliedTo(target)) return false
        if (target is Player && (target.isCreative || target.isSpectator)) return false
        return true
    }

    fun forEachHostileVictim(owner: LivingEntity, radius: Double, action: (LivingEntity) -> Unit) {
        val victims = owner.level().getEntitiesOfClass(LivingEntity::class.java, owner.boundingBox.inflate(radius))
        for (victim in victims) {
            if (!isHostileTarget(owner, victim)) continue
            action(victim)
        }
    }

    fun retargetNearestPlayer(owner: Monster, range: Double = 32.0) {
        if (owner.target != null) return
        val nearest = owner.level().getNearestPlayer(owner, range)
        if (nearest != null && isHostileTarget(owner, nearest)) {
            owner.target = nearest
        }
    }

    fun scaledAbilityCooldown(owner: LivingEntity, baseCooldown: Int): Int {
        val maxHealth = owner.maxHealth.coerceAtLeast(1.0f)
        val healthRatio = (owner.health / maxHealth).coerceIn(0.25f, 1.0f)
        val scaled = (baseCooldown * (0.6f + healthRatio * 0.4f)).toInt()
        return scaled.coerceAtLeast(baseCooldown / 2)
    }
}

