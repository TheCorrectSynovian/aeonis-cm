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
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase
import net.minecraft.world.entity.monster.Blaze
import net.minecraft.world.entity.monster.Ghast
import net.minecraft.world.entity.monster.Phantom
import net.minecraft.world.entity.monster.Vex
import net.minecraft.world.entity.monster.EnderMan
import net.minecraft.world.entity.monster.Creeper
import net.minecraft.world.entity.monster.AbstractSkeleton
import net.minecraft.world.entity.monster.breeze.Breeze
import net.minecraft.world.entity.monster.Pillager
import net.minecraft.world.entity.animal.Chicken
import net.minecraft.world.entity.projectile.Arrow
import net.minecraft.world.entity.projectile.WitherSkull
import net.minecraft.world.entity.projectile.DragonFireball
import net.minecraft.world.entity.projectile.SmallFireball
import net.minecraft.world.entity.projectile.LargeFireball
import net.minecraft.world.entity.projectile.windcharge.BreezeWindCharge
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

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
    val attack: Boolean,
    val teleport: Boolean = false
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
                buf.writeBoolean(payload.teleport)
            },
            { buf ->
                MobControlPayload(
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readBoolean(),
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
        
        // Special handling for Ender Dragon - it's a complex entity
        val isDragon = entity is EnderDragon
        
        // Keep dragon in hover phase to prevent AI interference and stop jittery AI turns
        if (isDragon && entity is EnderDragon) {
            entity.phaseManager.setPhase(EnderDragonPhase.HOVERING)
            if (entity.isNoAi) entity.setNoAi(false)
        }
        
        // Movement speed - slower in water, faster for flying mobs, dragon is big and fast
        val baseSpeed = when {
            isDragon -> 1.2  // Dragon is BIG and fast!
            isSwimming -> 0.25
            isFlying -> 0.45
            else -> 0.35
        }
        val speed = if (payload.sneak) baseSpeed * 0.4 else baseSpeed
        
        if (forward != 0.0 || strafe != 0.0) {
            if (isDragon && entity is EnderDragon) {
                // Dragon movement: use velocity+move for smoother camera; avoid teleport jitter
                val cosPitch = Math.cos(pitchRad)
                val sinPitch = Math.sin(pitchRad)

                val motionX = (-Math.sin(yawRad) * forward * cosPitch + Math.cos(yawRad) * strafe) * speed
                val motionY = (-sinPitch * forward) * speed
                val motionZ = (Math.cos(yawRad) * forward * cosPitch + Math.sin(yawRad) * strafe) * speed

                val motion = Vec3(motionX, motionY, motionZ)
                entity.deltaMovement = motion
                entity.move(net.minecraft.world.entity.MoverType.SELF, motion)

                // Hard-set rotations to kill wobble
                entity.setYRot(payload.yaw)
                entity.setXRot(payload.pitch)
                entity.yBodyRot = payload.yaw
                entity.yHeadRot = payload.yaw
                entity.yRotO = payload.yaw
                entity.xRotO = payload.pitch
            } else if (isFlying) {
                // Flying movement - full 3D movement based on pitch
                val cosPitch = Math.cos(pitchRad)
                val sinPitch = Math.sin(pitchRad)
                
                val motionX = (-Math.sin(yawRad) * forward * cosPitch + Math.cos(yawRad) * strafe) * speed
                var motionY = (-sinPitch * forward) * speed
                // Allow free ascent/descent with jump/sneak even while moving
                if (payload.jump) motionY += 0.35
                if (payload.sneak) motionY -= 0.35
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
        if (isDragon && entity is EnderDragon) {
            // Dragon hover / vertical control when idle
            if (forward == 0.0 && strafe == 0.0) {
                var dy = 0.0
                if (payload.jump) dy += 0.35
                if (payload.sneak) dy -= 0.35
                if (dy != 0.0) {
                    val motion = Vec3(0.0, dy, 0.0)
                    entity.deltaMovement = motion
                    entity.move(net.minecraft.world.entity.MoverType.SELF, motion)
                } else {
                    entity.deltaMovement = Vec3.ZERO
                }

                entity.setYRot(payload.yaw)
                entity.setXRot(payload.pitch)
                entity.yBodyRot = payload.yaw
                entity.yHeadRot = payload.yaw
                entity.yRotO = payload.yaw
                entity.xRotO = payload.pitch
            }
        } else if (isFlying) {
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
        
        // Handle attack - special handling for boss mobs and projectile mobs
        if (payload.attack && canAttack(entity)) {
            when (entity) {
                is WitherBoss -> handleWitherAttack(player, entity, payload.yaw, payload.pitch)
                is EnderDragon -> handleDragonAttack(player, entity, payload.yaw, payload.pitch)
                is Breeze -> handleBreezeAttack(player, entity, payload.yaw, payload.pitch)
                is Blaze -> handleBlazeAttack(player, entity, payload.yaw, payload.pitch)
                is Ghast -> handleGhastAttack(player, entity, payload.yaw, payload.pitch)
                is Chicken -> handleChickenEgg(player, entity)
                is Creeper -> handleCreeperExplosion(player, entity, payload.yaw, payload.pitch)
                is AbstractSkeleton -> handleSkeletonArrow(player, entity, payload.yaw, payload.pitch)
                is Pillager -> handlePillagerArrow(player, entity, payload.yaw, payload.pitch)
                else -> {
                    if (isGhastLike(entity)) {
                        handleGhastLikeAttack(player, entity as? LivingEntity ?: return)
                    } else {
                        handleMobAttack(player, entity, payload.yaw, payload.pitch)
                    }
                }
            }
        }
        
        // Handle Enderman teleport ability (T key)
        if (payload.teleport && entity is EnderMan) {
            handleEndermanTeleport(player, entity, payload.yaw, payload.pitch)
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
               isGhastLike(entity) ||
               entity is Phantom ||
               entity is WitherBoss ||
               entity is EnderDragon ||
               entity is Allay ||
               entity is Bee ||
               entity is Parrot ||
               entity is Vex ||
               entity is Blaze ||
               entity is Breeze
    }

    private fun isGhastLike(entity: Entity): Boolean {
        return try {
            val path = entity.type.builtInRegistryHolder().key().location().path
            path.contains("ghast", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
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
    
    /**
     * Handle Ender Dragon fireball attack
     */
    private fun handleDragonAttack(player: ServerPlayer, dragon: EnderDragon, yaw: Float, pitch: Float) {
        // Check cooldown (400ms between fireballs)
        val now = System.currentTimeMillis()
        val lastAttack = attackCooldowns[player.uuid] ?: 0L
        if (now - lastAttack < 400) return
        attackCooldowns[player.uuid] = now
        
        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return
        
        // Calculate look direction
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        
        val lookX = -Math.sin(yawRad) * Math.cos(pitchRad)
        val lookY = -Math.sin(pitchRad)
        val lookZ = Math.cos(yawRad) * Math.cos(pitchRad)
        
        // Spawn position - from dragon's head
        val spawnX = dragon.x + lookX * 4.0
        val spawnY = dragon.y + 3.0 + lookY * 4.0
        val spawnZ = dragon.z + lookZ * 4.0
        
        // Create dragon fireball
        val fireball = DragonFireball(level, dragon, Vec3(lookX, lookY, lookZ).normalize())
        fireball.setPos(spawnX, spawnY, spawnZ)
        
        // Set velocity
        val power = 1.2
        fireball.setDeltaMovement(lookX * power, lookY * power, lookZ * power)
        
        // Spawn the fireball
        level.addFreshEntity(fireball)
        
        // Play dragon shoot sound
        level.playSound(
            null,
            dragon.x, dragon.y, dragon.z,
            net.minecraft.sounds.SoundEvents.ENDER_DRAGON_SHOOT,
            net.minecraft.sounds.SoundSource.HOSTILE,
            1.0f,
            1.0f
        )
    }
    
    /**
     * Handle Breeze wind charge attack
     */
    private fun handleBreezeAttack(player: ServerPlayer, breeze: Breeze, yaw: Float, pitch: Float) {
        // Check cooldown (350ms between wind charges - fast and fun!)
        val now = System.currentTimeMillis()
        val lastAttack = attackCooldowns[player.uuid] ?: 0L
        if (now - lastAttack < 350) return
        attackCooldowns[player.uuid] = now
        
        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return
        
        // Calculate look direction
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        
        val lookX = -Math.sin(yawRad) * Math.cos(pitchRad)
        val lookY = -Math.sin(pitchRad)
        val lookZ = Math.cos(yawRad) * Math.cos(pitchRad)
        
        // Spawn position - from breeze's center
        val spawnX = breeze.x + lookX * 1.5
        val spawnY = breeze.y + 1.0 + lookY * 1.5
        val spawnZ = breeze.z + lookZ * 1.5
        
        // Create breeze wind charge
        val windCharge = BreezeWindCharge(breeze, level)
        windCharge.setPos(spawnX, spawnY, spawnZ)
        
        // Set velocity - wind charges are fast!
        val power = 1.8
        windCharge.deltaMovement = Vec3(lookX * power, lookY * power, lookZ * power)
        
        // Spawn the wind charge
        level.addFreshEntity(windCharge)
        
        // Play breeze shoot sound
        level.playSound(
            null,
            breeze.x, breeze.y, breeze.z,
            net.minecraft.sounds.SoundEvents.BREEZE_SHOOT,
            net.minecraft.sounds.SoundSource.HOSTILE,
            1.0f,
            1.0f
        )
    }
    
    /**
     * Handle Blaze fireball attack (rapid small fireballs)
     */
    private fun handleBlazeAttack(player: ServerPlayer, blaze: Blaze, yaw: Float, pitch: Float) {
        // Check cooldown (250ms between fireballs - rapid fire!)
        val now = System.currentTimeMillis()
        val lastAttack = attackCooldowns[player.uuid] ?: 0L
        if (now - lastAttack < 250) return
        attackCooldowns[player.uuid] = now
        
        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return
        
        // Calculate look direction
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        
        val lookX = -Math.sin(yawRad) * Math.cos(pitchRad)
        val lookY = -Math.sin(pitchRad)
        val lookZ = Math.cos(yawRad) * Math.cos(pitchRad)
        
        // Spawn position - from blaze's center
        val spawnX = blaze.x + lookX * 1.5
        val spawnY = blaze.y + 1.0 + lookY * 1.5
        val spawnZ = blaze.z + lookZ * 1.5
        
        // Create small fireball (like blaze shoots)
        val fireball = SmallFireball(level, blaze, Vec3(lookX, lookY, lookZ).normalize())
        fireball.setPos(spawnX, spawnY, spawnZ)
        
        // Set velocity
        val power = 1.5
        fireball.setDeltaMovement(lookX * power, lookY * power, lookZ * power)
        
        // Spawn the fireball
        level.addFreshEntity(fireball)
        
        // Play blaze shoot sound
        level.playSound(
            null,
            blaze.x, blaze.y, blaze.z,
            net.minecraft.sounds.SoundEvents.BLAZE_SHOOT,
            net.minecraft.sounds.SoundSource.HOSTILE,
            1.0f,
            1.0f
        )
    }
    
    /**
     * Handle Ghast fireball attack (large explosive fireballs)
     */
    private fun handleGhastAttack(player: ServerPlayer, ghast: Ghast, yaw: Float, pitch: Float) {
        // Check cooldown (600ms between fireballs - big and powerful!)
        val now = System.currentTimeMillis()
        val lastAttack = attackCooldowns[player.uuid] ?: 0L
        if (now - lastAttack < 600) return
        attackCooldowns[player.uuid] = now
        
        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return
        
        // Calculate look direction
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        
        val lookX = -Math.sin(yawRad) * Math.cos(pitchRad)
        val lookY = -Math.sin(pitchRad)
        val lookZ = Math.cos(yawRad) * Math.cos(pitchRad)
        
        // Spawn position - from ghast's face (ghasts are big!)
        val spawnX = ghast.x + lookX * 3.0
        val spawnY = ghast.y + 2.0 + lookY * 3.0
        val spawnZ = ghast.z + lookZ * 3.0
        
        // Create large fireball (like ghast shoots) - explosive power 1
        val fireball = LargeFireball(level, ghast, Vec3(lookX, lookY, lookZ).normalize(), 1)
        fireball.setPos(spawnX, spawnY, spawnZ)
        
        // Set velocity - ghast fireballs are slower but powerful
        val power = 1.0
        fireball.setDeltaMovement(lookX * power, lookY * power, lookZ * power)
        
        // Spawn the fireball
        level.addFreshEntity(fireball)
        
        // Play ghast shoot sound (the iconic fireball sound!)
        level.playSound(
            null,
            ghast.x, ghast.y, ghast.z,
            net.minecraft.sounds.SoundEvents.GHAST_SHOOT,
            net.minecraft.sounds.SoundSource.HOSTILE,
            1.0f,
            1.0f
        )
        
        // Also play the warning sound
        level.playSound(
            null,
            ghast.x, ghast.y, ghast.z,
            net.minecraft.sounds.SoundEvents.GHAST_WARN,
            net.minecraft.sounds.SoundSource.HOSTILE,
            0.5f,
            1.0f
        )
    }

    /**
     * Handle Ghast-like custom mobs (e.g., Happy Ghast) that are not instance of Ghast
     */
    private fun handleGhastLikeAttack(player: ServerPlayer, entity: LivingEntity) {
        // Check cooldown (600ms like ghast)
        val now = System.currentTimeMillis()
        val lastAttack = attackCooldowns[player.uuid] ?: 0L
        if (now - lastAttack < 600) return
        attackCooldowns[player.uuid] = now

        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return

        // Use player-facing yaw/pitch (entity rotation already synced)
        val yaw = entity.yRot
        val pitch = entity.xRot

        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())

        val lookX = -Math.sin(yawRad) * Math.cos(pitchRad)
        val lookY = -Math.sin(pitchRad)
        val lookZ = Math.cos(yawRad) * Math.cos(pitchRad)

        // Spawn position - in front of face
        val spawnX = entity.x + lookX * 3.0
        val spawnY = entity.y + entity.eyeHeight.toDouble() + lookY * 2.0
        val spawnZ = entity.z + lookZ * 3.0

        // Create large fireball (power 1 like ghast)
        val direction = Vec3(lookX, lookY, lookZ)
        val fireball = LargeFireball(level, entity, direction, 1)
        fireball.setPos(spawnX, spawnY, spawnZ)
        fireball.setDeltaMovement(direction.normalize())

        level.addFreshEntity(fireball)

        // Play ghast shoot sounds
        level.playSound(null, entity.x, entity.y, entity.z,
            net.minecraft.sounds.SoundEvents.GHAST_SHOOT,
            net.minecraft.sounds.SoundSource.HOSTILE,
            1.0f, 1.0f)
        level.playSound(null, entity.x, entity.y, entity.z,
            net.minecraft.sounds.SoundEvents.GHAST_WARN,
            net.minecraft.sounds.SoundSource.HOSTILE,
            0.5f, 1.0f)
    }

    /**
     * Handle Chicken egg drop (fun gag)
     */
    private fun handleChickenEgg(player: ServerPlayer, chicken: Chicken) {
        // Check cooldown (700ms between eggs)
        val now = System.currentTimeMillis()
        val lastAttack = attackCooldowns[player.uuid] ?: 0L
        if (now - lastAttack < 700) return
        attackCooldowns[player.uuid] = now

        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return

        // Spawn an egg item slightly above the chicken
        val spawnPos = Vec3(chicken.x, chicken.y + 0.6, chicken.z)
        val eggStack = ItemStack(Items.EGG, 1)
        val eggEntity = ItemEntity(level, spawnPos.x, spawnPos.y, spawnPos.z, eggStack)

        // Give it a little forward toss for fun
        val facing = chicken.lookAngle.normalize().scale(0.25)
        eggEntity.deltaMovement = Vec3(facing.x, 0.15, facing.z)

        level.addFreshEntity(eggEntity)

        // Play chicken egg sound
        level.playSound(
            null,
            chicken.x, chicken.y, chicken.z,
            net.minecraft.sounds.SoundEvents.CHICKEN_EGG,
            net.minecraft.sounds.SoundSource.NEUTRAL,
            1.0f,
            1.0f
        )
    }

    /**
     * Handle Creeper explosion - explode when left click is pressed near anything
     * The creeper will die in the explosion like the real mob
     */
    private fun handleCreeperExplosion(player: ServerPlayer, creeper: Creeper, yaw: Float, pitch: Float) {
        // Check cooldown (1500ms to prevent spam - also acts as "charging" time)
        val now = System.currentTimeMillis()
        val lastAttack = attackCooldowns[player.uuid] ?: 0L
        if (now - lastAttack < 1500) return
        attackCooldowns[player.uuid] = now

        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return

        // Calculate look direction to check if there's something nearby to explode on
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())

        val lookX = -Math.sin(yawRad) * Math.cos(pitchRad)
        val lookY = -Math.sin(pitchRad)
        val lookZ = Math.cos(yawRad) * Math.cos(pitchRad)
        val lookVec = Vec3(lookX, lookY, lookZ).normalize()

        // Check if there's an entity or block within 4 blocks in front
        val checkRange = 4.0
        val startPos = creeper.position().add(0.0, creeper.bbHeight * 0.5, 0.0)
        val endPos = startPos.add(lookVec.scale(checkRange))

        // Check for nearby entities
        val nearbyEntities = level.getEntitiesOfClass(
            LivingEntity::class.java,
            AABB(
                minOf(startPos.x, endPos.x) - 2.0,
                minOf(startPos.y, endPos.y) - 2.0,
                minOf(startPos.z, endPos.z) - 2.0,
                maxOf(startPos.x, endPos.x) + 2.0,
                maxOf(startPos.y, endPos.y) + 2.0,
                maxOf(startPos.z, endPos.z) + 2.0
            )
        ) { it != creeper && it != player && it.isAlive }

        // Check for blocks in front (raycast)
        val blockHitResult = level.clip(net.minecraft.world.level.ClipContext(
            startPos,
            endPos,
            net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE,
            creeper
        ))
        val hasBlockNearby = blockHitResult.type == net.minecraft.world.phys.HitResult.Type.BLOCK

        // Only explode if there's something nearby (entity or block)
        if (nearbyEntities.isEmpty() && !hasBlockNearby) {
            player.displayClientMessage(Component.literal("§c💥 Get closer to something to explode!"), true)
            return
        }

        // Play creeper hiss sound before explosion
        level.playSound(
            null,
            creeper.x, creeper.y, creeper.z,
            net.minecraft.sounds.SoundEvents.CREEPER_PRIMED,
            net.minecraft.sounds.SoundSource.HOSTILE,
            1.0f,
            1.0f
        )

        // Determine explosion power (charged creeper = bigger explosion)
        val explosionPower = if (creeper.isPowered) 6.0f else 3.0f

        // Create the explosion at creeper's position
        level.explode(
            creeper,
            creeper.x, creeper.y + creeper.bbHeight * 0.5, creeper.z,
            explosionPower,
            net.minecraft.world.level.Level.ExplosionInteraction.MOB
        )

        // Remove the player from controlling the entity first
        removeControlledEntity(player)

        // Kill the creeper (they die when they explode)
        creeper.kill(level)

        // Teleport player to where they were spectating from
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL)

        // Notify the player
        player.displayClientMessage(Component.literal("§c💥 BOOM! Your creeper exploded!"), false)
    }

    /**
     * Handle Skeleton arrow shooting - shoot arrows using the bow in hand
     * Works for Skeleton, Stray, Wither Skeleton, and Bogged
     */
    private fun handleSkeletonArrow(player: ServerPlayer, skeleton: AbstractSkeleton, yaw: Float, pitch: Float) {
        // Check cooldown (800ms between shots - similar to skeleton attack speed)
        val now = System.currentTimeMillis()
        val lastAttack = attackCooldowns[player.uuid] ?: 0L
        if (now - lastAttack < 800) return
        attackCooldowns[player.uuid] = now

        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return

        // Calculate look direction
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())

        val lookX = -Math.sin(yawRad) * Math.cos(pitchRad)
        val lookY = -Math.sin(pitchRad)
        val lookZ = Math.cos(yawRad) * Math.cos(pitchRad)
        val lookVec = Vec3(lookX, lookY, lookZ).normalize()

        // Spawn arrow from skeleton's eye position
        val spawnPos = Vec3(skeleton.x, skeleton.eyeY - 0.1, skeleton.z)

        // Create the arrow
        val arrow = Arrow(level, skeleton, ItemStack(Items.ARROW), null)
        arrow.setPos(spawnPos.x, spawnPos.y, spawnPos.z)

        // Set arrow velocity (speed similar to skeleton's shot)
        val arrowSpeed = 1.6
        arrow.shoot(lookVec.x, lookVec.y, lookVec.z, arrowSpeed.toFloat(), 1.0f)

        // Add the arrow to the world
        level.addFreshEntity(arrow)

        // Swing arm animation
        skeleton.swing(net.minecraft.world.InteractionHand.MAIN_HAND)

        // Play bow shoot sound
        level.playSound(
            null,
            skeleton.x, skeleton.y, skeleton.z,
            net.minecraft.sounds.SoundEvents.ARROW_SHOOT,
            net.minecraft.sounds.SoundSource.HOSTILE,
            1.0f,
            1.0f / (level.random.nextFloat() * 0.4f + 0.8f)
        )
    }

    /**
     * Handle Pillager crossbow arrow shooting
     */
    private fun handlePillagerArrow(player: ServerPlayer, pillager: Pillager, yaw: Float, pitch: Float) {
        // Check cooldown (1000ms between shots - crossbow takes longer)
        val now = System.currentTimeMillis()
        val lastAttack = attackCooldowns[player.uuid] ?: 0L
        if (now - lastAttack < 1000) return
        attackCooldowns[player.uuid] = now

        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return

        // Calculate look direction
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())

        val lookX = -Math.sin(yawRad) * Math.cos(pitchRad)
        val lookY = -Math.sin(pitchRad)
        val lookZ = Math.cos(yawRad) * Math.cos(pitchRad)
        val lookVec = Vec3(lookX, lookY, lookZ).normalize()

        // Spawn arrow from pillager's eye position
        val spawnPos = Vec3(pillager.x, pillager.eyeY - 0.1, pillager.z)

        // Create the arrow (crossbow arrows are faster and more accurate)
        val arrow = Arrow(level, pillager, ItemStack(Items.ARROW), null)
        arrow.setPos(spawnPos.x, spawnPos.y, spawnPos.z)
        arrow.setCritArrow(true) // Crossbow arrows are always critical

        // Set arrow velocity (crossbow is faster than bow)
        val arrowSpeed = 3.15
        arrow.shoot(lookVec.x, lookVec.y, lookVec.z, arrowSpeed.toFloat(), 0.0f) // 0 inaccuracy for crossbow

        // Add the arrow to the world
        level.addFreshEntity(arrow)

        // Swing arm animation
        pillager.swing(net.minecraft.world.InteractionHand.MAIN_HAND)

        // Play crossbow shoot sound
        level.playSound(
            null,
            pillager.x, pillager.y, pillager.z,
            net.minecraft.sounds.SoundEvents.CROSSBOW_SHOOT,
            net.minecraft.sounds.SoundSource.HOSTILE,
            1.0f,
            1.0f / (level.random.nextFloat() * 0.4f + 0.8f)
        )
    }
    
    /**
     * Handle Enderman teleport ability (60 blocks in facing direction)
     */
    private fun handleEndermanTeleport(player: ServerPlayer, enderman: EnderMan, yaw: Float, pitch: Float) {
        // Check cooldown (1000ms between teleports)
        val now = System.currentTimeMillis()
        val lastTeleport = attackCooldowns[player.uuid] ?: 0L
        if (now - lastTeleport < 1000) return
        attackCooldowns[player.uuid] = now
        
        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return
        
        // Calculate teleport direction
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        
        val lookX = -Math.sin(yawRad) * Math.cos(pitchRad)
        val lookY = -Math.sin(pitchRad)
        val lookZ = Math.cos(yawRad) * Math.cos(pitchRad)
        
        // Teleport 60 blocks in facing direction
        val teleportDistance = 60.0
        var targetX = enderman.x + lookX * teleportDistance
        var targetY = enderman.y + lookY * teleportDistance
        var targetZ = enderman.z + lookZ * teleportDistance
        
        // Clamp Y to valid range
        targetY = targetY.coerceIn(-64.0, 320.0)
        
        // Try to find a safe landing spot (check for solid ground)
        var foundSafe = false
        for (yOffset in 0..10) {
            val checkY = targetY - yOffset
            val blockPos = net.minecraft.core.BlockPos.containing(targetX, checkY, targetZ)
            val belowPos = blockPos.below()
            
            if (level.getBlockState(belowPos).isSolid &&
                !level.getBlockState(blockPos).isSolid &&
                !level.getBlockState(blockPos.above()).isSolid) {
                targetY = checkY
                foundSafe = true
                break
            }
        }
        
        // If no safe spot found, try above
        if (!foundSafe) {
            for (yOffset in 1..10) {
                val checkY = targetY + yOffset
                val blockPos = net.minecraft.core.BlockPos.containing(targetX, checkY, targetZ)
                val belowPos = blockPos.below()
                
                if (level.getBlockState(belowPos).isSolid &&
                    !level.getBlockState(blockPos).isSolid &&
                    !level.getBlockState(blockPos.above()).isSolid) {
                    targetY = checkY
                    foundSafe = true
                    break
                }
            }
        }
        
        // Teleport the enderman
        enderman.teleportTo(targetX, targetY, targetZ)
        
        // Play enderman teleport sounds at both locations
        level.playSound(
            null,
            enderman.xo, enderman.yo, enderman.zo,
            net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
            net.minecraft.sounds.SoundSource.HOSTILE,
            1.0f,
            1.0f
        )
        level.playSound(
            null,
            targetX, targetY, targetZ,
            net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
            net.minecraft.sounds.SoundSource.HOSTILE,
            1.0f,
            1.0f
        )
        
        // Spawn particles at destination
        level.sendParticles(
            net.minecraft.core.particles.ParticleTypes.PORTAL,
            targetX, targetY + 1.0, targetZ,
            50, 0.5, 1.0, 0.5, 0.1
        )
        
        // Notify player
        player.displayClientMessage(Component.literal("§5✦ Teleported!"), true)
    }
}
