package com.qc.aeonis.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.qc.aeonis.network.AeonisNetworking;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.spider.Spider;

@Mixin(Spider.class)
public abstract class ControlledSpiderClimbMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void aeonis$enableControlledSpiderClimbing(CallbackInfo ci) {
        Spider self = (Spider) (Object) this;
        if (!(self.level() instanceof ServerLevel)) {
            return;
        }
        if (!AeonisNetworking.INSTANCE.isEntityControlled(self.getId())) {
            return;
        }

        // Keep vanilla spider climbing behavior active while player controls it.
        self.setClimbing(self.horizontalCollision);
        if (self.horizontalCollision && self.onGround()) {
            self.setDeltaMovement(self.getDeltaMovement().x, Math.max(0.21, self.getDeltaMovement().y), self.getDeltaMovement().z);
        }
    }
}
