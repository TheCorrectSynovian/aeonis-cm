package com.qc.aeonis.mixin;

import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Parrot.class)
public interface ParrotEntityAccessor {
    @Invoker("setNearbySongPlaying")
    void invokeSetNearbySongPlaying(BlockPos pos, boolean playing);

    @Invoker("flapWings")
    void invokeFlapWings();
}
