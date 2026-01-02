package com.qc.aeonis.mixin;

import com.qc.aeonis.network.AeonisNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents suffocation damage for players controlling mobs.
 * Small mobs (under 1 block) should never report being "in wall".
 */
@Mixin(Entity.class)
public class PlayerWallMixin {
    @Inject(method = "isInWall", at = @At("HEAD"), cancellable = true)
    private void onIsInsideWall(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        
        if (self instanceof ServerPlayer player) {
            if (AeonisNetworking.INSTANCE.isPlayerTransformed(player.getUUID())) {
                Integer controlledId = AeonisNetworking.INSTANCE.getControlledEntityId(player.getUUID());
                if (controlledId != null) {
                    var entity = player.level().getEntity(controlledId);
                    if (entity instanceof Mob mob) {
                        // Small mobs don't suffocate in walls at all
                        if (mob.getBbHeight() <= 1.0f) {
                            cir.setReturnValue(false);
                        }
                    }
                }
            }
        }
    }
}
