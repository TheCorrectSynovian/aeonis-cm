package com.qc.aeonis.mixin;

import com.qc.aeonis.network.AeonisNetworking;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class PlayerInsideWallMixin {
    @Inject(method = "isInsideWall", at = @At("HEAD"), cancellable = true)
    private void onIsInsideWall(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) self;
            Integer controlledId = AeonisNetworking.INSTANCE.getControlledEntityId(player.getUUID());
            if (controlledId != null) {
                Entity mob = player.level().getEntity(controlledId);
                if (mob != null && mob.getBbHeight() <= 1.0f) {
                    cir.setReturnValue(false);
                }
            }
        }
    }
}
