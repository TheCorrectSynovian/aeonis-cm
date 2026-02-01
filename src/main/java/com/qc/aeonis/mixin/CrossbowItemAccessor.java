package com.qc.aeonis.mixin;

import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CrossbowItem.class)
public interface CrossbowItemAccessor {
    @Invoker("getPullProgress")
    static float invokeGetPullProgress(int useTicks, ItemStack stack) {
        throw new AssertionError();
    }
}
