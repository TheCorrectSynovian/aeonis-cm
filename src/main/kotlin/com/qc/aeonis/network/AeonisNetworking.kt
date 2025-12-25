package com.qc.aeonis.network

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.animal.wolf.Wolf
import net.minecraft.world.entity.animal.IronGolem
import net.minecraft.world.entity.animal.allay.Allay
import net.minecraft.world.entity.animal.Bee
import net.minecraft.world.entity.animal.Parrot
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.monster.Blaze
import net.minecraft.world.entity.monster.Ghast
import net.minecraft.world.entity.monster.Phantom
import net.minecraft.world.entity.monster.Vex
import net.minecraft.world.entity.projectile.WitherSkull
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

/**
 * Packet sent from client to server with movement input
 */
data class MobControlPayload(
    val forward: Float,
    val strafe: Float,
    val jump: Boolean,
    val sneak: Boolean,
    val yaw: Float,
    val pitch: Float,
    val attack: Boolean
) : CustomPacketPayload {
    
    companion object {
        val ID = CustomPacketPayload.Type<MobControlPayload>(
            ResourceLocation.fromNamespaceAndPath("aeonis-manager", "mob_control")
        )
        
        val CODEC: StreamCodec<FriendlyByteBuf, MobControlPayload> = StreamCodec.of(
            { buf, payload ->
                buf.writeFloat(payload.forward)
                buf.writeFloat(payload.strafe)
                buf.writeBoolean(payload.jump)
                buf.writeBoolean(payload.sneak)
                buf.writeFloat(payload.yaw)
                buf.writeFloat(payload.pitch)
                buf.writeBoolean(payload.attack)
            },
            { buf ->
                MobControlPayload(
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readBoolean()
                )
            }
        )
    }
    
    override fun type(): CustomPacketPayload.Type<MobControlPayload> = ID
}

/**
 * Packet sent from server to client to enable/disable control mode
 */
data class ControlModePayload(
    val enabled: Boolean
) : CustomPacketPayload {
    
    companion object {
        val ID = CustomPacketPayload.Type<ControlModePayload>(
            ResourceLocation.fromNamespaceAndPath("aeonis-manager", "control_mode")
        )
        
        val CODEC: StreamCodec<FriendlyByteBuf, ControlModePayload> = StreamCodec.of(
            { buf, payload -> buf.writeBoolean(payload.enabled) },
            { buf -> ControlModePayload(buf.readBoolean()) }
        )
    }
    
    override fun type(): CustomPacketPayload.Type<ControlModePayload> = ID
}

object AeonisNetworking {
    
    // Server-side storage of controlled entities
    private val controlledEntities = mutableMapOf<java.util.UUID, Int>()
    
    // Track attack cooldown to prevent spam
    private val attackCooldowns = mutableMapOf<java.util.UUID, Long>()
    
    fun registerServer() {
        // Register payload types
        PayloadTypeRegistry.playC2S().register(MobControlPayload.ID, MobControlPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(ControlModePayload.ID, ControlModePayload.CODEC)
        
        // Handle incoming movement packets
        ServerPlayNetworking.registerGlobalReceiver(MobControlPayload.ID) { payload, context ->
            val player = context.player()
            handleMobControl(player, payload)
        }
    }
    
    /**
     * Check if a player is currently transformed
     */
    fun isPlayerTransformed(playerUuid: java.util.UUID): Boolean {
        return controlledEntities.containsKey(playerUuid)
    }
    
    fun setControlledEntity(player: ServerPlayer, entityId: Int) {
        controlledEntities[player.uuid] = entityId
        // Tell client to start controlling
        ServerPlayNetworking.send(player, ControlModePayload(true))
    }
    
    fun removeControlledEntity(player: ServerPlayer) {
        controlledEntities.remove(player.uuid)
        attackCooldowns.remove(player.uuid)
        // Tell client to stop controlling
        ServerPlayNetworking.send(player, ControlModePayload(false))
    }
    
    fun getControlledEntityId(playerUuid: java.util.UUID): Int? {
        return controlledEntities[playerUuid]
    }
    
    private fun handleMobControl(player: ServerPlayer, payload: MobControlPayload) {
        val entityId = controlledEntities[player.uuid] ?: return
        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return
        val entity = level.getEntity(entityId) ?: return
        
        // Apply rotation to entity
        entity.yRot = payload.yaw
        entity.xRot = payload.pitch
        entity.setYHeadRot(payload.yaw)
        if (entity is LivingEntity) {
            entity.yBodyRot = payload.yaw
        }
        
        // Calculate movement direction based on entity's rotation
        val yawRad = Math.toRadians(payload.yaw.toDouble())
        val pitchRad = Math.toRadians(payload.pitch.toDouble())
        val forward = payload.forward.toDouble()
        val strafe = payload.strafe.toDouble()
        
        // Check if in water or lava
        val inWater = entity.isInWater
        val inLava = entity.isInLava
        val isSwimming = inWater || inLava
        
        // Check if this is a flying mob
        val isFlying = isFlightMob(entity)
        
        // Movement speed - slower in water, faster for flying mobs
        val baseSpeed = when {
            isSwimming -> 0.25
            isFlying -> 0.45
            else -> 0.35
        }
        val speed = if (payload.sneak) baseSpeed * 0.4 else baseSpeed
        
        if (forward != 0.0 || strafe != 0.0) {
            if (isFlying) {
                // Flying movement - full 3D movement based on pitch
                val cosPitch = Math.cos(pitchRad)
                val sinPitch = Math.sin(pitchRad)
                
                val motionX = (-Math.sin(yawRad) * forward * cosPitch + Math.cos(yawRad) * strafe) * speed
                val motionY = (-sinPitch * forward) * speed
                val motionZ = (Math.cos(yawRad) * forward * cosPitch + Math.sin(yawRad) * strafe) * speed
                
                entity.setDeltaMovement(Vec3(motionX, motionY, motionZ))
                entity.move(net.minecraft.world.entity.MoverType.SELF, Vec3(motionX, motionY, motionZ))
            } else if (isSwimming) {
                // Swimming movement - include vertical component based on pitch
                val cosPitch = Math.cos(pitchRad)
                val sinPitch = Math.sin(pitchRad)
                
                val motionX = (-Math.sin(yawRad) * forward * cosPitch + Math.cos(yawRad) * strafe) * speed
                val motionY = (-sinPitch * forward) * speed * 0.8
                val motionZ = (Math.cos(yawRad) * forward * cosPitch + Math.sin(yawRad) * strafe) * speed
                
                entity.setDeltaMovement(Vec3(motionX, motionY, motionZ))
                entity.move(net.minecraft.world.entity.MoverType.SELF, Vec3(motionX, motionY, motionZ))
                
                // Set swimming pose
                if (entity is LivingEntity) {
                    entity.setSwimming(true)
                }
            } else {
                // Normal ground movement
                val motionX = (-Math.sin(yawRad) * forward + Math.cos(yawRad) * strafe) * speed
                val motionZ = (Math.cos(yawRad) * forward + Math.sin(yawRad) * strafe) * speed
                
                entity.move(net.minecraft.world.entity.MoverType.SELF, Vec3(motionX, 0.0, motionZ))
                
                if (entity is LivingEntity) {
                    entity.setSwimming(false)
                }
            }
        } else if (entity is LivingEntity) {
            entity.setSwimming(false)
        }
        
        // Apply physics based on environment
        if (isFlying) {
            // Flying mobs hover in place when not moving
            if (forward == 0.0 && strafe == 0.0) {
                if (payload.jump) {
                    // Fly up when holding jump
                    entity.setDeltaMovement(entity.deltaMovement.x, 0.3, entity.deltaMovement.z)
                    entity.move(net.minecraft.world.entity.MoverType.SELF, Vec3(0.0, 0.3, 0.0))
                } else if (payload.sneak) {
                    // Fly down when holding sneak
                    entity.setDeltaMovement(entity.deltaMovement.x, -0.3, entity.deltaMovement.z)
                    entity.move(net.minecraft.world.entity.MoverType.SELF, Vec3(0.0, -0.3, 0.0))
                } else {
                    // Hover in place - no gravity
                    entity.setDeltaMovement(entity.deltaMovement.x, 0.0, entity.deltaMovement.z)
                }
            }
        } else if (isSwimming) {
            // Buoyancy in water - float up slowly when not moving down
            val currentY = entity.deltaMovement.y
            if (!payload.sneak && forward == 0.0) {
                // Gentle float up
                val buoyancy = if (inLava) 0.02 else 0.04
                entity.setDeltaMovement(entity.deltaMovement.x, currentY + buoyancy, entity.deltaMovement.z)
                entity.move(net.minecraft.world.entity.MoverType.SELF, Vec3(0.0, buoyancy, 0.0))
            } else if (payload.sneak) {
                // Sink when sneaking
                val sink = if (inLava) -0.03 else -0.06
                entity.setDeltaMovement(entity.deltaMovement.x, sink, entity.deltaMovement.z)
                entity.move(net.minecraft.world.entity.MoverType.SELF, Vec3(0.0, sink, 0.0))
            }
        } else if (!entity.onGround()) {
            // Normal gravity when not in water (and not flying)
            val gravity = entity.deltaMovement.y - 0.08
            entity.setDeltaMovement(entity.deltaMovement.x, gravity, entity.deltaMovement.z)
            entity.move(net.minecraft.world.entity.MoverType.SELF, Vec3(0.0, gravity, 0.0))
        }
        
        // Handle jumping - works on ground OR to swim up in water (flying mobs handled above)
        if (payload.jump && !isFlying) {
            if (entity.onGround() && !isSwimming) {
                // Normal jump
                entity.setDeltaMovement(entity.deltaMovement.x, 0.42, entity.deltaMovement.z)
                entity.move(net.minecraft.world.entity.MoverType.SELF, Vec3(0.0, 0.42, 0.0))
            } else if (isSwimming) {
                // Swim up
                val swimUpSpeed = if (inLava) 0.06 else 0.12
                entity.setDeltaMovement(entity.deltaMovement.x, swimUpSpeed, entity.deltaMovement.z)
                entity.move(net.minecraft.world.entity.MoverType.SELF, Vec3(0.0, swimUpSpeed, 0.0))
            }
        }
        
        // Handle sneaking visual
        entity.isShiftKeyDown = payload.sneak
        
        // Handle attack - special handling for Wither
        if (payload.attack && canAttack(entity)) {
            if (entity is WitherBoss) {
                handleWitherAttack(player, entity, payload.yaw, payload.pitch)
            } else {
                handleMobAttack(player, entity, payload.yaw, payload.pitch)
            }
        }
        
        // Show entity health and held item via action bar
        sendEntityStatus(player, entity)
        
        // Keep player camera synced to entity
        player.setCamera(entity)
    }
    
    private fun sendEntityStatus(player: ServerPlayer, entity: Entity) {
        if (entity is LivingEntity) {
            val health = entity.health.toInt()
            val maxHealth = entity.maxHealth.toInt()
            val healthBar = buildHealthBar(health, maxHealth)
            
            // Get held item info
            val mainHand = entity.getItemBySlot(EquipmentSlot.MAINHAND)
            val offHand = entity.getItemBySlot(EquipmentSlot.OFFHAND)
            
            val heldInfo = buildHeldItemInfo(mainHand, offHand)
            
            // Send as action bar
            val message = Component.literal("§c❤ $healthBar §7($health/$maxHealth) $heldInfo")
            player.displayClientMessage(message, true)
        }
    }
    
    private fun buildHealthBar(health: Int, maxHealth: Int): String {
        val filledBars = ((health.toFloat() / maxHealth.toFloat()) * 10).toInt()
        val emptyBars = 10 - filledBars
        return "§c" + "█".repeat(filledBars) + "§8" + "█".repeat(emptyBars)
    }
    
    private fun buildHeldItemInfo(mainHand: ItemStack, offHand: ItemStack): String {
        val parts = mutableListOf<String>()
        
        if (!mainHand.isEmpty) {
            parts.add("§f⚔ ${mainHand.hoverName.string}")
        }
        if (!offHand.isEmpty) {
            parts.add("§f🛡 ${offHand.hoverName.string}")
        }
        
        return if (parts.isEmpty()) "" else "§7| ${parts.joinToString(" ")}"
    }
    
    private fun canAttack(entity: Entity): Boolean {
        // Check if this entity type can attack
        return entity is Monster || 
               entity is Wolf ||
               entity is IronGolem ||
               entity is Mob // Most mobs can attack
    }
    
    private fun handleMobAttack(player: ServerPlayer, entity: Entity, yaw: Float, pitch: Float) {
        // Check cooldown (500ms between attacks)
        val now = System.currentTimeMillis()
        val lastAttack = attackCooldowns[player.uuid] ?: 0L
        if (now - lastAttack < 500) return
        attackCooldowns[player.uuid] = now
        
        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return
        
        // Calculate look direction
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        
        val lookX = -Math.sin(yawRad) * Math.cos(pitchRad)
        val lookY = -Math.sin(pitchRad)
        val lookZ = Math.cos(yawRad) * Math.cos(pitchRad)
        val lookVec = Vec3(lookX, lookY, lookZ).normalize()
        
        // Get attack reach based on entity size
        val reach = if (entity is LivingEntity) {
            entity.bbWidth.toDouble().coerceAtLeast(3.0) + 2.0
        } else {
            4.0
        }
        
        // Create attack box in front of entity
        val attackStart = entity.position().add(0.0, entity.bbHeight * 0.5, 0.0)
        val attackEnd = attackStart.add(lookVec.scale(reach))
        
        val attackBox = AABB(
            minOf(attackStart.x, attackEnd.x) - 1.0,
            minOf(attackStart.y, attackEnd.y) - 1.0,
            minOf(attackStart.z, attackEnd.z) - 1.0,
            maxOf(attackStart.x, attackEnd.x) + 1.0,
            maxOf(attackStart.y, attackEnd.y) + 1.0,
            maxOf(attackStart.z, attackEnd.z) + 1.0
        )
        
        // Find entities in attack range
        val targets = level.getEntitiesOfClass(
            LivingEntity::class.java,
            attackBox
        ) { target ->
            target != entity && 
            target != player && 
            target.isAlive &&
            !target.isSpectator
        }
        
        // Attack the closest target in the direction we're looking
        var closestTarget: LivingEntity? = null
        var closestDist = Double.MAX_VALUE
        
        for (target in targets) {
            val toTarget = target.position().subtract(entity.position()).normalize()
            val dot = lookVec.dot(toTarget)
            
            // Only attack if target is roughly in front (dot product > 0.5 means within ~60 degrees)
            if (dot > 0.3) {
                val dist = entity.distanceToSqr(target)
                if (dist < closestDist) {
                    closestDist = dist
                    closestTarget = target
                }
            }
        }
        
        // Perform the attack
        closestTarget?.let { target ->
            if (entity is Mob) {
                // Use the mob's actual attack
                entity.setTarget(target)
                entity.doHurtTarget(level, target)
                entity.setTarget(null)
                
                // Play attack sound
                entity.playAmbientSound()
            } else if (entity is LivingEntity) {
                // Generic attack for other living entities
                target.hurt(level.damageSources().mobAttack(entity), 4.0f)
            }
            
            // Swing arm animation
            if (entity is LivingEntity) {
                entity.swing(net.minecraft.world.InteractionHand.MAIN_HAND)
            }
        }
    }
    
    /**
     * Check if an entity is a flying mob that needs 3D flight controls
     */
    private fun isFlightMob(entity: Entity): Boolean {
        return entity is Ghast ||
               entity is Phantom ||
               entity is WitherBoss ||
               entity is EnderDragon ||
               entity is Allay ||
               entity is Bee ||
               entity is Parrot ||
               entity is Vex ||
               entity is Blaze
    }
    
    /**
     * Handle Wither skull attack
     */
    private fun handleWitherAttack(player: ServerPlayer, wither: WitherBoss, yaw: Float, pitch: Float) {
        // Check cooldown (300ms between skull shots - faster than melee)
        val now = System.currentTimeMillis()
        val lastAttack = attackCooldowns[player.uuid] ?: 0L
        if (now - lastAttack < 300) return
        attackCooldowns[player.uuid] = now
        
        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return
        
        // Calculate look direction
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        
        val lookX = -Math.sin(yawRad) * Math.cos(pitchRad)
        val lookY = -Math.sin(pitchRad)
        val lookZ = Math.cos(yawRad) * Math.cos(pitchRad)
        
        // Spawn position - from wither's head
        val spawnX = wither.x + lookX * 2.0
        val spawnY = wither.y + 3.0 + lookY * 2.0
        val spawnZ = wither.z + lookZ * 2.0
        
        // Create wither skull
        val skull = WitherSkull(level, wither, Vec3(lookX, lookY, lookZ).normalize())
        skull.setPos(spawnX, spawnY, spawnZ)
        skull.setOwner(wither)
        
        // Set power (velocity)
        val power = 1.5
        skull.setDeltaMovement(lookX * power, lookY * power, lookZ * power)
        
        // Spawn the skull
        level.addFreshEntity(skull)
        
        // Play wither shoot sound
        level.playSound(
            null,
            wither.x, wither.y, wither.z,
            net.minecraft.sounds.SoundEvents.WITHER_SHOOT,
            net.minecraft.sounds.SoundSource.HOSTILE,
            1.0f,
            1.0f
        )
    }
}
