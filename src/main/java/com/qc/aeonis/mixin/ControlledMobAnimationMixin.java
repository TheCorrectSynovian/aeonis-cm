package com.qc.aeonis.mixin;

import com.qc.aeonis.network.AeonisNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class ControlledMobAnimationMixin {
    @Inject(method = "calculateEntityAnimation", at = @At("TAIL"))
    private void aeonis$syncControlledWalkAnim(boolean useY, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self.level() instanceof ServerLevel)) {
            return;
        }
        if (!AeonisNetworking.INSTANCE.isEntityControlled(self.getId())) {
            return;
        }

        double dx = self.getX() - self.xo;
        double dz = self.getZ() - self.zo;
        float horizontal = (float) Math.sqrt(dx * dx + dz * dz);
        float targetSpeed = Mth.clamp(horizontal * 9.0F, 0.0F, 2.8F);
        self.walkAnimation.setSpeed(targetSpeed);
    }
}
