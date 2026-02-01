package com.qc.aeonis.mixin.client;

import com.qc.aeonis.network.AeonisClientNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Custom crosshair targeting that excludes the controlled mob
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    
    @Shadow
    @Final
    private Minecraft minecraft;
    
    @Inject(method = "pick(F)V", at = @At("HEAD"), cancellable = true)
    private void aeonis$customPick(float tickDelta, CallbackInfo ci) {
        if (!AeonisClientNetworking.INSTANCE.isControlling() || AeonisClientNetworking.INSTANCE.getControlledMobId() <= 0) {
            return;
        }
        
        ci.cancel();
        
        Entity cameraEntity = this.minecraft.getCameraEntity();
        if (cameraEntity == null || this.minecraft.level == null) {
            this.minecraft.crosshairPickEntity = null;
            return;
        }
        
        double reach = this.minecraft.player.hasCorrectToolForDrops(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()) ? 6.0 : 4.5;
        Vec3 eyePos = cameraEntity.getEyePosition(tickDelta);
        Vec3 lookVec = cameraEntity.getViewVector(tickDelta);
        Vec3 endPos = eyePos.add(lookVec.scale(reach));
        
        // Find closest entity (excluding controlled mob)
        Entity closestEntity = null;
        double closestDistanceSq = reach * reach;
        int controlledId = AeonisClientNetworking.INSTANCE.getControlledMobId();
        
        AABB searchBox = cameraEntity.getBoundingBox().expandTowards(lookVec.scale(reach)).inflate(1.0);
        
        for (Entity entity : this.minecraft.level.getEntities(cameraEntity, searchBox, e -> 
                e.getId() != controlledId && !e.isSpectator() && e.isPickable())) {
            AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
            java.util.Optional<Vec3> hitOpt = box.clip(eyePos, endPos);
            if (hitOpt.isPresent()) {
                double distSq = eyePos.distanceToSqr(hitOpt.get());
                if (distSq < closestDistanceSq) {
                    closestDistanceSq = distSq;
                    closestEntity = entity;
                }
            }
        }
        
        // Block raycast
        BlockHitResult blockHit = this.minecraft.level.clip(new ClipContext(
            eyePos, endPos,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            cameraEntity
        ));
        
        double entityDistSq = closestEntity != null ? closestDistanceSq : Double.MAX_VALUE;
        double blockDistSq = blockHit.getType() != HitResult.Type.MISS ? eyePos.distanceToSqr(blockHit.getLocation()) : Double.MAX_VALUE;
        
        if (closestEntity != null && entityDistSq <= blockDistSq) {
            this.minecraft.crosshairPickEntity = closestEntity;
            AABB box = closestEntity.getBoundingBox().inflate(closestEntity.getPickRadius());
            java.util.Optional<Vec3> hitOpt = box.clip(eyePos, endPos);
            Vec3 hitPos = hitOpt.orElse(new Vec3(closestEntity.getX(), closestEntity.getY(), closestEntity.getZ()));
            this.minecraft.hitResult = new EntityHitResult(closestEntity, hitPos);
        } else if (blockHit.getType() != HitResult.Type.MISS) {
            this.minecraft.crosshairPickEntity = null;
            this.minecraft.hitResult = blockHit;
        } else {
            this.minecraft.crosshairPickEntity = null;
            this.minecraft.hitResult = BlockHitResult.miss(endPos, null, null);
        }
    }
}
