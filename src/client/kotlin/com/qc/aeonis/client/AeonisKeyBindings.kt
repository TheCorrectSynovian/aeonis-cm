package com.qc.aeonis.client

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
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

    // Call from ClientModInitializer.onInitializeClient to register bindings when safe
    fun register() {
        try {
            KeyBindingHelper.registerKeyBinding(TELEPORT_KEY)
            KeyBindingHelper.registerKeyBinding(POSSESS_KEY)
            KeyBindingHelper.registerKeyBinding(RELEASE_KEY)
            KeyBindingHelper.registerKeyBinding(ABILITY_KEY)
            KeyBindingHelper.registerKeyBinding(SOUL_POSSESS_KEY)
        } catch (e: Exception) {
            // Avoid hard crashes if registration is called unexpectedly; log if needed
        }
    }
}
