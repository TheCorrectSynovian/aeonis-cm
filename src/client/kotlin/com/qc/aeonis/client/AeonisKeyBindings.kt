package com.qc.aeonis.client

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW

object AeonisKeyBindings {
    private val CATEGORY = KeyMapping.Category.register(Identifier("aeonis", "controls"))

    val TELEPORT_KEY: KeyMapping = KeyBindingHelper.registerKeyBinding(
        KeyMapping("key.aeonis.teleport", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_T, CATEGORY)
    )

    val POSSESS_KEY: KeyMapping = KeyBindingHelper.registerKeyBinding(
        KeyMapping("key.aeonis.possess", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY)
    )

    val RELEASE_KEY: KeyMapping = KeyBindingHelper.registerKeyBinding(
        KeyMapping("key.aeonis.release", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY)
    )

    val ABILITY_KEY: KeyMapping = KeyBindingHelper.registerKeyBinding(
        KeyMapping("key.aeonis.ability", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY)
    )

    val SOUL_POSSESS_KEY: KeyMapping = KeyBindingHelper.registerKeyBinding(
        KeyMapping("key.aeonis.soul_possess", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, CATEGORY)
    )
}
