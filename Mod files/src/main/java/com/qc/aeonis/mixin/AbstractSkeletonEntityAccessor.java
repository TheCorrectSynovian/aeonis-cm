package com.qc.aeonis.mixin;

import net.minecraft.world.entity.monster.Skeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Skeleton.class)
public interface AbstractSkeletonEntityAccessor {
    @Invoker("setAttacking")
    void invokeSetAttacking(boolean attacking);
}
