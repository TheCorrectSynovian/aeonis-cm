package com.qc.aeonis.mixin;

import com.qc.aeonis.network.AeonisNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Small mobs (under 1 block) ignore soul sand, honey, and other slow movement blocks.
 * Also prevents small mobs from being pushed out of blocks.
 */
@Mixin(Entity.class)
public class PlayerSlowMovementMixin {
    @Inject(method = "makeStuckInBlock(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), cancellable = true)
    private void onSlowMovement(BlockState state, Vec3 multiplier, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        
        if (self instanceof ServerPlayer player) {
            if (AeonisNetworking.INSTANCE.isPlayerTransformed(player.getUUID())) {
                Integer controlledId = AeonisNetworking.INSTANCE.getControlledEntityId(player.getUUID());
                if (controlledId != null) {
                    var entity = player.level().getEntity(controlledId);
                    if (entity instanceof Mob mob && mob.getBbHeight() <= 1.0f) {
                        // Small mobs ignore slowdown effects
                        ci.cancel();
                    }
                }
            }
        }
    }

    @Inject(method = "checkInsideBlocks()V", at = @At("HEAD"), cancellable = true)
    private void onCheckInsideBlocks(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        
        if (self instanceof ServerPlayer player) {
            if (AeonisNetworking.INSTANCE.isPlayerTransformed(player.getUUID())) {
                Integer controlledId = AeonisNetworking.INSTANCE.getControlledEntityId(player.getUUID());
                if (controlledId != null) {
                    var entity = player.level().getEntity(controlledId);
                    if (entity instanceof Mob mob && mob.getBbHeight() <= 1.0f) {
                        // Small mobs don't get pushed out of blocks
                        ci.cancel();
                    }
                }
            }
        }
    }
}
