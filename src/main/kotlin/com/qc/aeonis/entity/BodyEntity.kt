package com.qc.aeonis.entity

import com.qc.aeonis.AeonisPossession
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Mob
import java.util.UUID

class BodyEntity(entityType: EntityType<out BodyEntity>, world: Level) : PathfinderMob(entityType, world) {
    private var initialHeadYaw: Float = 0f
    private var initialBodyYaw: Float = 0f
    private var initialPitch: Float = 0f
    private var rotationSet: Boolean = false

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        builder.define(OWNER_UUID, "")
        builder.define(SYNCED_X, 0f)
        builder.define(SYNCED_Y, 0f)
        builder.define(SYNCED_Z, 0f)
    }

    fun setInitialHeadRotation(yaw: Float, pitch: Float) {
        initialHeadYaw = yaw
        initialBodyYaw = yaw
        initialPitch = pitch
        rotationSet = true
        yRot = yaw
        yHeadRot = yaw
        yBodyRot = yaw
        xRot = pitch
    }

    override fun aiStep() {
        super.aiStep()

        if (rotationSet) {
            yRot = initialHeadYaw
            yHeadRot = initialBodyYaw
            yBodyRot = initialBodyYaw
            xRot = initialPitch
        }

        if (!level().isClientSide) {
            setPersistenceRequired()
            entityData.set(SYNCED_X, x.toFloat())
            entityData.set(SYNCED_Y, y.toFloat())
            entityData.set(SYNCED_Z, z.toFloat())
        }
    }

    override fun travel(movementInput: Vec3) {
        if (!level().isClientSide) {
            var vel = deltaMovement
            vel = when {
                isInWater -> vel.multiply(0.6, 0.98, 0.6)
                isFallFlying -> vel.multiply(0.5, 0.5, 0.5)
                else -> vel.multiply(0.98, 0.98, 0.98)
            }
            if (!onGround()) vel = vel.add(0.0, -0.08, 0.0)
            setDeltaMovement(vel)
        } else {
            super.travel(movementInput)
        }
    }

    fun getSyncedX(): Double = entityData.get(SYNCED_X).toDouble()
    fun getSyncedY(): Double = entityData.get(SYNCED_Y).toDouble()
    fun getSyncedZ(): Double = entityData.get(SYNCED_Z).toDouble()

    override fun die(source: DamageSource) {
        super.die(source)
        if (!level().isClientSide) {
            val storedInv = AeonisPossession.mobInventories.remove(id)
            if (storedInv != null) {
                val serverLevel = level() as? net.minecraft.server.level.ServerLevel ?: return
                for (stack in storedInv) {
                    if (stack != null) spawnAtLocation(serverLevel, stack)
                }
            }
        }
    }

    companion object {
        private val OWNER_UUID: EntityDataAccessor<String> = SynchedEntityData.defineId(BodyEntity::class.java, EntityDataSerializers.STRING)
        private val SYNCED_X: EntityDataAccessor<Float> = SynchedEntityData.defineId(BodyEntity::class.java, EntityDataSerializers.FLOAT)
        private val SYNCED_Y: EntityDataAccessor<Float> = SynchedEntityData.defineId(BodyEntity::class.java, EntityDataSerializers.FLOAT)
        private val SYNCED_Z: EntityDataAccessor<Float> = SynchedEntityData.defineId(BodyEntity::class.java, EntityDataSerializers.FLOAT)

        fun createBodyAttributes(): AttributeSupplier.Builder = Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.25)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.08)
    }

    fun setOwnerUuid(uuid: UUID?) {
        entityData.set(OWNER_UUID, uuid?.toString() ?: "")
    }

    fun getOwnerUuid(): UUID? {
        val uuidStr = entityData.get(OWNER_UUID)
        if (uuidStr.isEmpty()) return null
        return try { UUID.fromString(uuidStr) } catch (e: IllegalArgumentException) { null }
    }

    override fun isInvulnerableTo(serverLevel: net.minecraft.server.level.ServerLevel, source: DamageSource): Boolean {
        if (super.isInvulnerableTo(serverLevel, source)) return true
        val owner = getOwnerUuid()
        if (owner != null && AeonisPossession.isOriginalModeSpectator(owner)) return true
        return false
    }

    override fun isPickable(): Boolean = true
}
