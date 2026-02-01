package com.qc.aeonis.mixin.client;

import com.qc.aeonis.network.AeonisClientNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Handles client-side collision bypass when controlling a mob
 */
@Mixin(Entity.class)
public abstract class ClientCollisionMixin {
    
    @Shadow
    public abstract int getId();
    
    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void aeonis$onPushAway(Entity entity, CallbackInfo ci) {
        Entity self = (Entity)(Object)this;
        
        // Only process on client side
        if (!self.level().isClientSide()) {
            return;
        }
        
        // Player shouldn't be pushed by controlled mob
        if (self instanceof LocalPlayer && AeonisClientNetworking.INSTANCE.isControlling()) {
            if (AeonisClientNetworking.INSTANCE.getControlledMobId() == entity.getId()) {
                ci.cancel();
                return;
            }
        }
        
        // Controlled mob shouldn't be pushed by player
        if (entity instanceof LocalPlayer && AeonisClientNetworking.INSTANCE.isControlling()) {
            if (AeonisClientNetworking.INSTANCE.getControlledMobId() == self.getId()) {
                ci.cancel();
            }
        }
    }
    
    @Inject(method = "canCollideWith", at = @At("HEAD"), cancellable = true)
    private void aeonis$onCanCollideWith(Entity other, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity)(Object)this;
        
        if (!self.level().isClientSide()) {
            return;
        }
        
        if (AeonisClientNetworking.INSTANCE.isControlling()) {
            int controlledId = AeonisClientNetworking.INSTANCE.getControlledMobId();
            
            // Player can't collide with controlled mob
            if (self instanceof LocalPlayer && controlledId == other.getId()) {
                cir.setReturnValue(false);
                return;
            }
            
            // Controlled mob can't collide with player
            if (other instanceof LocalPlayer && controlledId == self.getId()) {
                cir.setReturnValue(false);
            }
        }
    }
    
    @Inject(method = "isAttackable", at = @At("HEAD"), cancellable = true)
    private void aeonis$onIsAttackable(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity)(Object)this;
        
        if (self.level().isClientSide() && AeonisClientNetworking.INSTANCE.isControlling()) {
            if (AeonisClientNetworking.INSTANCE.getControlledMobId() == self.getId()) {
                cir.setReturnValue(false);
            }
        }
    }
    
    @Inject(method = "isNoGravity", at = @At("HEAD"), cancellable = true)
    private void aeonis$controlledMobNoGravityCheck(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity)(Object)this;
        
        // Controlled mob uses player's gravity state
        if (self.level().isClientSide() && AeonisClientNetworking.INSTANCE.isControlling()) {
            if (AeonisClientNetworking.INSTANCE.getControlledMobId() == self.getId()) {
                // Return true if player is flying/no gravity
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null && mc.player.getAbilities().flying) {
                    cir.setReturnValue(true);
                }
            }
        }
    }
    
    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void aeonis$controlledMobNotPushable(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity)(Object)this;
        
        if (self.level().isClientSide() && AeonisClientNetworking.INSTANCE.isControlling()) {
            // Controlled mob cannot be pushed
            if (AeonisClientNetworking.INSTANCE.getControlledMobId() == self.getId()) {
                cir.setReturnValue(false);
            }
            // Player cannot be pushed when controlling
            if (self instanceof LocalPlayer) {
                cir.setReturnValue(false);
            }
        }
    }
}
