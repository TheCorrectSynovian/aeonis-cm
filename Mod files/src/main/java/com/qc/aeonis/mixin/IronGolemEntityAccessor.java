package com.qc.aeonis.mixin;

import net.minecraft.world.entity.animal.IronGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(IronGolem.class)
public interface IronGolemEntityAccessor {
    @Accessor("attackTicksLeft")
    void setAttackTicksLeft(int ticks);

    @Accessor("attackTicksLeft")
    int getAttackTicksLeft();
}
