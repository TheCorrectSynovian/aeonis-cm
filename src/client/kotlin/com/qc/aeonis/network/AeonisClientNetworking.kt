package com.qc.aeonis.network

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import com.qc.aeonis.client.AeonisKeyBindings

object AeonisClientNetworking {
    
    private var isControlling = false
    private var controlledMobId: Int = -1
    private val controllingPlayerUUIDs = mutableSetOf<java.util.UUID>()
    private var wasAttacking = false  // Track previous attack state to detect new clicks
    private var wasTeleporting = false  // Track previous teleport state to detect new T press
    
    // Attack timing: distinguish short click (melee) vs long click (block breaking)
    private var attackStartTime: Long = 0L
    private var attackTriggered = false  // Whether we've already triggered an attack this press
    private const val MELEE_CLICK_THRESHOLD_MS = 150L  // Clicks shorter than this = melee attack
    
    // Track if we're currently breaking a block (long press mode)
    private var isBreakingBlock = false

    // Tracked body position (client-side mirror for release fallback)
    private var trackedBodyX = 0.0
    private var trackedBodyY = 0.0
    private var trackedBodyZ = 0.0

    // Hotbar tracking
    private var prevSelectedSlot: Int = -1

    // Controlled entity last-known status (updated by server)
    private var controlledEntityId: Int = -1
    private var controlledEntityHealth: Float = 0f
    private var controlledEntityMaxHealth: Float = 20f
    private var controlledEntityName: String = ""
    
    // Soul mode (spectator mode with ability to possess existing mobs)
    private var isInSoulMode = false

    private val TELEPORT_KEY get() = AeonisKeyBindings.TELEPORT_KEY
    private val POSSESS_KEY get() = AeonisKeyBindings.POSSESS_KEY
    private val RELEASE_KEY get() = AeonisKeyBindings.RELEASE_KEY
    private val ABILITY_KEY get() = AeonisKeyBindings.ABILITY_KEY
    private val SOUL_POSSESS_KEY get() = AeonisKeyBindings.SOUL_POSSESS_KEY
    
    fun register() {
        // Note: Payload types are already registered by the server/common code
        // We only need to register handlers here
        // Initialize prevSelectedSlot to player's current slot if available
        try {
            val mc = Minecraft.getInstance()
            if (mc.player != null) prevSelectedSlot = mc.player!!.inventory.selectedSlot
        } catch (_: Exception) {}
        
        // Handle control mode packet from server (now includes mobId and selectedSlot)
        ClientPlayNetworking.registerGlobalReceiver(ControlModePayload.ID) { payload, _ ->
            val mc = Minecraft.getInstance()
            isControlling = payload.enabled && payload.mobId > 0
            controlledMobId = if (payload.mobId > 0) payload.mobId else -1
            // If server told us which slot to select, apply it with a small delay to avoid race conditions
            if (payload.selectedSlot >= 0 && mc.player != null) {
                val slot = payload.selectedSlot
                mc.execute {
                    try {
                        mc.player!!.inventory.selectedSlot = slot
                    } catch (_: Exception) {
                        // ignore if mapping different
                    }
                }
            }
        }

        // Handle sync controlling players payload
        ClientPlayNetworking.registerGlobalReceiver(AeonisNetworking.SyncControllingPlayersPayload.ID) { payload, _ ->
            val players = payload.playerUUIDs
            // update local set
            controllingPlayerUUIDs.clear()
            controllingPlayerUUIDs.addAll(players)
        }

        // Handle controlled-entity status updates from server
        ClientPlayNetworking.registerGlobalReceiver(AeonisNetworking.ControlledEntityStatusPayload.ID) { payload, _ ->
            try {
                controlledEntityId = payload.entityId
                controlledEntityHealth = payload.health
                controlledEntityMaxHealth = payload.maxHealth
                controlledEntityName = payload.name
            } catch (_: Exception) {}
        }
        
        // Handle soul mode status from server
        ClientPlayNetworking.registerGlobalReceiver(AeonisNetworking.SoulModePayload.ID) { payload, _ ->
            isInSoulMode = payload.enabled
        }
        
        // Handle open manhunt GUI packet from server
        ClientPlayNetworking.registerGlobalReceiver(AeonisNetworking.OpenManhuntGuiPayload.ID) { _, _ ->
            val mc = Minecraft.getInstance()
            mc.execute {
                mc.setScreen(com.qc.aeonis.screen.ManhuntSetupScreen())
            }
        }

        // Register tick event to send movement inputs
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (isControlling && client.player != null && client.level != null) {
                sendMovementInput(client)
            }

            // Possess key: try to resolve the targeted mob under crosshair and send its id
            if (client.player != null && POSSESS_KEY.isDown) {
                val selectedSlot = client.player!!.inventory.selectedSlot
                try {
                    val hit = client.hitResult
                    if (hit is net.minecraft.world.phys.EntityHitResult) {
                        val ent = hit.entity
                        if (ent is net.minecraft.world.entity.LivingEntity) {
                            val payload = PossessPayload(ent.id, selectedSlot)
                            ClientPlayNetworking.send(payload)
                        } else {
                            client.player!!.displayClientMessage(Component.literal("§c[AEONIS] Can only control living mobs!"), false)
                        }
                    } else {
                        client.player!!.displayClientMessage(Component.literal("§c[AEONIS] Look at a mob to control it!"), false)
                    }
                } catch (_: Exception) {
                    // fallback: send with -1 (server will ignore)
                    val payload = PossessPayload(-1, selectedSlot)
                    ClientPlayNetworking.send(payload)
                }
            }

            if (client.player != null && RELEASE_KEY.isDown) {
                val selectedSlot = client.player!!.inventory.selectedSlot
                val payload = ReleasePayload(selectedSlot, client.player!!.x, client.player!!.y, client.player!!.z)
                ClientPlayNetworking.send(payload)
                // clear tracked body after release
                trackedBodyX = 0.0
                trackedBodyY = 0.0
                trackedBodyZ = 0.0
            }

            if (client.player != null && ABILITY_KEY.isDown) {
                ClientPlayNetworking.send(AbilityPayload())
            }
            
            // Soul Possess key (P): when in soul mode, possess the mob you're looking at
            if (client.player != null && isInSoulMode && SOUL_POSSESS_KEY.consumeClick()) {
                try {
                    val hit = client.hitResult
                    if (hit is net.minecraft.world.phys.EntityHitResult) {
                        val ent = hit.entity
                        if (ent is net.minecraft.world.entity.Mob) {
                            // Send soul possess packet to server
                            ClientPlayNetworking.send(AeonisNetworking.SoulPossessPayload(ent.id))
                        } else {
                            client.player!!.displayClientMessage(Component.literal("§c[AEONIS] Can only possess mobs!"), false)
                        }
                    } else {
                        client.player!!.displayClientMessage(Component.literal("§c[AEONIS] Look at a mob to possess it!"), false)
                    }
                } catch (_: Exception) {
                    client.player!!.displayClientMessage(Component.literal("§c[AEONIS] Error possessing mob!"), false)
                }
            }

            // Update tracked body coordinates for owner's body entity if present
            if (isControlling && client.player != null && client.level != null) {
                val playerUuid = client.player!!.uuid
                for (entity in client.level!!.entitiesForRendering()) {
                    if (entity is com.qc.aeonis.entity.BodyEntity) {
                        val owner = entity.getOwnerUuid()
                        if (owner != null && owner == playerUuid) {
                            trackedBodyX = entity.x
                            trackedBodyY = entity.y
                            trackedBodyZ = entity.z
                            break
                        }
                    }
                }
                
                // Client-side visual sync: update mob's rotation to match player's camera for smooth rendering
                // Position is controlled entirely by the server to avoid desync
                if (controlledMobId > 0) {
                    val mobEntity = client.level!!.getEntity(controlledMobId)
                    if (mobEntity is net.minecraft.world.entity.Mob) {
                        // Only sync rotation for smooth visual feedback
                        // Position comes from server via handleMobControl
                        mobEntity.yRot = client.player!!.yRot
                        mobEntity.xRot = client.player!!.xRot
                        mobEntity.yBodyRot = client.player!!.yRot
                        mobEntity.yHeadRot = client.player!!.yRot
                        
                        // Animate limbs based on player movement for visual feedback
                        if (mobEntity is net.minecraft.world.entity.LivingEntity) {
                            val horizontalSpeed = Math.sqrt(
                                client.player!!.deltaMovement.x * client.player!!.deltaMovement.x +
                                client.player!!.deltaMovement.z * client.player!!.deltaMovement.z
                            )
                            // This triggers walk animation
                            mobEntity.walkAnimation.setSpeed((horizontalSpeed * 6.5f).toFloat())
                        }
                    }
                }

                // Hotbar change detection: notify server when player changes selected slot
                try {
                    val currSlot = client.player!!.inventory.selectedSlot
                    if (currSlot != prevSelectedSlot) {
                        prevSelectedSlot = currSlot
                        ClientPlayNetworking.send(SelectedSlotPayload(currSlot))
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun setControlling(controlling: Boolean) {
        isControlling = controlling
    }
    
    fun isControlling(): Boolean = isControlling

    fun getControlledEntityHealth(): Float = controlledEntityHealth
    fun getControlledEntityMaxHealth(): Float = controlledEntityMaxHealth
    fun getControlledEntityName(): String = controlledEntityName
    fun getControlledEntityId(): Int = controlledEntityId
    fun getControlledMobId(): Int = controlledMobId
    fun isBreakingBlock(): Boolean = isBreakingBlock
    
    private fun sendMovementInput(client: Minecraft) {
        val player = client.player ?: return
        val options = client.options
        
        // Get movement input
        var forward = 0f
        var strafe = 0f
        
        if (options.keyUp.isDown) forward += 1f
        if (options.keyDown.isDown) forward -= 1f
        if (options.keyLeft.isDown) strafe += 1f
        if (options.keyRight.isDown) strafe -= 1f
        
        val jump = options.keyJump.isDown
        val sneak = options.keyShift.isDown
        
        // Get camera rotation
        val yaw = player.yRot
        val pitch = player.xRot
        
        // Detect left click attack with short vs long click logic
        // Short click = melee attack by mob
        // Long click = block breaking by player
        val attackKeyDown = options.keyAttack.isDown
        var attack = false
        val currentTime = System.currentTimeMillis()
        
        if (attackKeyDown && !wasAttacking) {
            // Just started pressing - record start time
            attackStartTime = currentTime
            attackTriggered = false
            isBreakingBlock = false
        } else if (attackKeyDown && !attackTriggered) {
            // Still holding - check duration
            val holdDuration = currentTime - attackStartTime
            if (holdDuration >= MELEE_CLICK_THRESHOLD_MS) {
                // Long press - switch to block breaking mode
                isBreakingBlock = true
            }
        } else if (!attackKeyDown && wasAttacking) {
            // Just released - check if it was a short click
            val holdDuration = currentTime - attackStartTime
            if (holdDuration < MELEE_CLICK_THRESHOLD_MS && !attackTriggered) {
                // Short click released - trigger melee attack
                attack = true
                attackTriggered = true
            }
            isBreakingBlock = false
        }
        wasAttacking = attackKeyDown
        
        // Detect T key for teleport (Enderman ability)
        val teleportKeyDown = TELEPORT_KEY.isDown
        val teleport = teleportKeyDown && !wasTeleporting
        wasTeleporting = teleportKeyDown
        
        // Send packet to server
        val payload = MobControlPayload(forward, strafe, jump, sneak, yaw, pitch, attack, teleport)
        ClientPlayNetworking.send(payload)
    }
}
