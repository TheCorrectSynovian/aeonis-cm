package com.qc.aeonis.mixin;

import net.minecraft.world.entity.monster.piglin.Piglin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Piglin.class)
public interface PiglinEntityAccessor {
    @Invoker("setDancing")
    void invokeSetDancing(boolean dancing);
}
