package com.qc.aeonis.mixin;

import net.minecraft.world.entity.animal.frog.Frog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Frog.class)
public interface FrogEntityAccessor {
    // Accessor for Frog internals if needed
}
