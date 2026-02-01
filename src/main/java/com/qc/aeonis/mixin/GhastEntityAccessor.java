package com.qc.aeonis.mixin;

import net.minecraft.world.entity.monster.Ghast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Ghast.class)
public interface GhastEntityAccessor {
    @Invoker("setShooting")
    void invokeSetShooting(boolean shooting);
}
