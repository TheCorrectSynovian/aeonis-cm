package com.qc.aeonis.network

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft

object AeonisClientNetworking {
    
    private var isControlling = false
    private var wasAttacking = false  // Track previous attack state to detect new clicks
    
    fun register() {
        // Note: Payload types are already registered by the server/common code
        // We only need to register handlers here
        
        // Handle control mode packet from server
        ClientPlayNetworking.registerGlobalReceiver(ControlModePayload.ID) { payload, _ ->
            isControlling = payload.enabled
        }
        
        // Register tick event to send movement inputs
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (isControlling && client.player != null && client.level != null) {
                sendMovementInput(client)
            }
        }
    }
    
    fun setControlling(controlling: Boolean) {
        isControlling = controlling
    }
    
    fun isControlling(): Boolean = isControlling
    
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
        
        // Detect left click attack (only trigger on new press, not held)
        val attackKeyDown = options.keyAttack.isDown
        val attack = attackKeyDown && !wasAttacking
        wasAttacking = attackKeyDown
        
        // Send packet to server
        val payload = MobControlPayload(forward, strafe, jump, sneak, yaw, pitch, attack)
        ClientPlayNetworking.send(payload)
    }
}
