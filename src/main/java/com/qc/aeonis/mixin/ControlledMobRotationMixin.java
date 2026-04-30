package com.qc.aeonis.mixin;

import com.qc.aeonis.network.AeonisNetworking;
import com.qc.aeonis.network.MobControlPayload;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public class ControlledMobRotationMixin {
    @Inject(method = "aiStep", at = @At("TAIL"))
    private void aeonis$smoothControlledRotation(CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (!(self.level() instanceof ServerLevel)) {
            return;
        }

        UUID controller = AeonisNetworking.INSTANCE.getControllingPlayerUuid(self.getId());
        if (controller == null) {
            return;
        }

        MobControlPayload input = AeonisNetworking.INSTANCE.getLatestControlInput(controller);
        if (input == null) {
            return;
        }

        float targetYaw = input.getYaw();
        float smoothedHead = Mth.rotLerp(0.5F, self.getYHeadRot(), targetYaw);
        float smoothedBody = Mth.rotLerp(0.35F, self.yBodyRot, smoothedHead);

        self.setYHeadRot(smoothedHead);
        self.yBodyRot = smoothedBody;
    }
}
