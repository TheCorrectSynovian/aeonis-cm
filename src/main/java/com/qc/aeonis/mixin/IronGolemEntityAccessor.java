package com.qc.aeonis.mixin;

import net.minecraft.world.entity.animal.golem.IronGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(IronGolem.class)
public interface IronGolemEntityAccessor {
    @Accessor("attackAnimationTick")
    void setAttackAnimationTick(int ticks);

    @Accessor("attackAnimationTick")
    int getAttackAnimationTick();
}
