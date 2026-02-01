package com.qc.aeonis.mixin;

import com.qc.aeonis.network.AeonisNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents collision between the invisible player and all entities while transformed.
 * The controlled mob handles all collision instead.
 */
@Mixin(Entity.class)
public class EntityCollisionMixin {
    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void onPushAway(Entity other, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        
        // Skip client-side
        if (self.level().isClientSide()) {
            return;
        }
        
        // Case 1: Transformed player shouldn't push or be pushed by anything
        if (self instanceof ServerPlayer player) {
            if (AeonisNetworking.INSTANCE.isPlayerTransformed(player.getUUID())) {
                ci.cancel();
                return;
            }
        }
        
        // Case 2: Nothing should push a transformed player
        if (other instanceof ServerPlayer player) {
            if (AeonisNetworking.INSTANCE.isPlayerTransformed(player.getUUID())) {
                ci.cancel();
                return;
            }
        }
    }
    
    @Inject(method = "canCollideWith", at = @At("HEAD"), cancellable = true)
    private void onCanCollideWith(Entity other, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        
        // Skip client-side
        if (self.level().isClientSide()) {
            return;
        }
        
        // Transformed player cannot collide with anything
        if (self instanceof ServerPlayer player) {
            if (AeonisNetworking.INSTANCE.isPlayerTransformed(player.getUUID())) {
                cir.setReturnValue(false);
                return;
            }
        }
        
        // Nothing can collide with a transformed player
        if (other instanceof ServerPlayer player) {
            if (AeonisNetworking.INSTANCE.isPlayerTransformed(player.getUUID())) {
                cir.setReturnValue(false);
            }
        }
    }
    
    @Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
    private void onIsPickable(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        
        // Transformed players are not pickable (can't be targeted by projectiles, etc.)
        if (self instanceof ServerPlayer player) {
            if (AeonisNetworking.INSTANCE.isPlayerTransformed(player.getUUID())) {
                cir.setReturnValue(false);
            }
        }
    }
    
    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void onIsPushable(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        
        // Skip client-side
        if (self.level().isClientSide()) {
            return;
        }
        
        // Transformed player is not pushable
        if (self instanceof ServerPlayer player) {
            if (AeonisNetworking.INSTANCE.isPlayerTransformed(player.getUUID())) {
                cir.setReturnValue(false);
                return;
            }
        }
        
        // Controlled mob is not pushable - check if this entity's ID is in the controlled entities map
        int selfId = self.getId();
        for (Integer controlledId : AeonisNetworking.INSTANCE.getControlledEntitiesMap().values()) {
            if (controlledId == selfId) {
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
