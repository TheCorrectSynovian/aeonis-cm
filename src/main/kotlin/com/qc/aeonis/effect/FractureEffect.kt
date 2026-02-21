package com.qc.aeonis.effect

import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.resources.Identifier

/**
 * "Fracture" debuff: makes targets move and attack more sluggishly.
 * Simple placeholder implementation (no custom client shader) by design.
 */
class FractureEffect : MobEffect(MobEffectCategory.HARMFUL, 0x5b5b5b) {
    init {
        addAttributeModifier(
            Attributes.MOVEMENT_SPEED,
            Identifier.fromNamespaceAndPath("aeonis", "fracture_speed"),
            -0.15,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        )
        addAttributeModifier(
            Attributes.ATTACK_DAMAGE,
            Identifier.fromNamespaceAndPath("aeonis", "fracture_damage"),
            -0.20,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        )
    }
}
