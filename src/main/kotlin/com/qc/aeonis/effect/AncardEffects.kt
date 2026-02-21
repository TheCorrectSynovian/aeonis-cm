package com.qc.aeonis.effect

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.effect.MobEffect

/**
 * Status effects used by Ancard mobs.
 *
 * NOTE: No spawn rules are registered anywhere here.
 */
object AncardEffects {
    private val FRACTURE_KEY: ResourceKey<MobEffect> = ResourceKey.create(
        Registries.MOB_EFFECT,
        Identifier.fromNamespaceAndPath("aeonis", "fracture")
    )

    val FRACTURE: MobEffect = Registry.register(
        BuiltInRegistries.MOB_EFFECT,
        FRACTURE_KEY,
        FractureEffect()
    )

    fun register() {
        // Force class init / keep a consistent entrypoint.
        @Suppress("UNUSED_VARIABLE")
        val fracture = FRACTURE
    }
}
