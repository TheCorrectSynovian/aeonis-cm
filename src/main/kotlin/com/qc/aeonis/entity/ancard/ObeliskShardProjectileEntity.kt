package com.qc.aeonis.entity.ancard

import net.minecraft.sounds.SoundEvents
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult

/**
 * Slow, mildly homing shard projectile used by the Obelisk Sentinel.
 *
 * Rendered as an item via ThrownItemRenderer on the client.
 */
class ObeliskShardProjectileEntity : ThrowableItemProjectile {
    constructor(type: EntityType<out ObeliskShardProjectileEntity>, level: Level) : super(type, level)
    constructor(type: EntityType<out ObeliskShardProjectileEntity>, owner: LivingEntity, level: Level) :
        super(type, owner, level, Items.AMETHYST_SHARD.defaultInstance)

    override fun getDefaultItem(): Item = Items.AMETHYST_SHARD

    override fun tick() {
        super.tick()
        if (!level().isClientSide) {
            val ownerMob = owner as? Mob
            val target = ownerMob?.target
            if (target != null && target.isAlive) {
                val to = target.eyePosition.subtract(position())
                val desired = to.normalize().scale(0.05)
                val current = deltaMovement
                deltaMovement = current.add(desired).scale(0.98)
            }
        }
    }

    override fun onHitEntity(result: EntityHitResult) {
        super.onHitEntity(result)
        if (!level().isClientSide) {
            val entity = result.entity
            val owner = owner as? LivingEntity
            val source = damageSources().mobProjectile(this, owner)
            entity.hurt(source, 6.0f)
            if (entity is LivingEntity) {
                entity.addEffect(MobEffectInstance(MobEffects.SLOWNESS, 60, 1))
            }
            playSound(SoundEvents.AMETHYST_CLUSTER_BREAK, 0.7f, 1.2f)
            discard()
        }
    }

    override fun onHit(result: HitResult) {
        super.onHit(result)
        if (!level().isClientSide) {
            if (result.type != HitResult.Type.ENTITY) {
                playSound(SoundEvents.AMETHYST_CLUSTER_BREAK, 0.5f, 1.0f)
                discard()
            }
        }
    }
}
