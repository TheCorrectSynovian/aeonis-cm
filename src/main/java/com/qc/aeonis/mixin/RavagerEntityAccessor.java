package com.qc.aeonis.mixin;

import net.minecraft.world.entity.monster.Ravager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Ravager.class)
public interface RavagerEntityAccessor {
    @Accessor("attackTick")
    void setAttackTick(int tick);

    @Accessor("attackTick")
    int getAttackTick();
}
