package com.qc.aeonis.client

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW

object AeonisKeyBindings {
    // Create category and KeyMapping instances but defer registration until client init.
    private val CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("aeonis", "controls"))

    val TELEPORT_KEY: KeyMapping = KeyMapping("key.aeonis.teleport", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_T, CATEGORY)
    val POSSESS_KEY: KeyMapping = KeyMapping("key.aeonis.possess", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY)
    val RELEASE_KEY: KeyMapping = KeyMapping("key.aeonis.release", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY)
    val ABILITY_KEY: KeyMapping = KeyMapping("key.aeonis.ability", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY)
    val SOUL_POSSESS_KEY: KeyMapping = KeyMapping("key.aeonis.soul_possess", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, CATEGORY)
    val ZOOM_KEY: KeyMapping = KeyMapping("key.aeonis.zoom", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY)
    val FREECAM_TOGGLE_KEY: KeyMapping = KeyMapping("key.aeonis.freecam_toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY)
    val FREECAM_REMOTE_BREAK_KEY: KeyMapping = KeyMapping("key.aeonis.freecam_remote_break", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY)

    // Call from ClientModInitializer.onInitializeClient to register bindings when safe
    fun register() {
        // Key mappings are defined and accessed directly in this 26.1 migration branch.
        // Leave registration as a no-op to avoid depending on unavailable helper APIs.
    }
}
