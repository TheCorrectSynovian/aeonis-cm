package com.qc.aeonis.mixin;

import net.minecraft.world.entity.animal.Bee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Bee.class)
public interface BeeEntityAccessor {
    @Invoker("setHasStung")
    void invokeSetHasStung(boolean stung);
}
