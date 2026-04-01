package com.qc.aeonis.mixin.client;

import com.qc.aeonis.client.AeonisKeyBindings;
import com.qc.aeonis.client.AeonisFreecam;
import com.qc.aeonis.network.AeonisClientNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Custom crosshair targeting that excludes the controlled mob
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    private static float aeonis$zoomProgress = 0.0F;
    private static final float AEONIS_ZOOM_FOV_MULTIPLIER = 0.35F;

    
    @Shadow
    @Final
    private Minecraft minecraft;
    
    @Inject(method = "pick", at = @At("HEAD"), cancellable = true, require = 0)
    private void aeonis$customPick(float tickDelta, CallbackInfo ci) {
        if (AeonisFreecam.INSTANCE.isEnabled()) {
            ci.cancel();
            aeonis$freecamPick(tickDelta);
            return;
        }

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

    private void aeonis$freecamPick(float tickDelta) {
        LocalPlayer player = this.minecraft.player;
        if (player == null || this.minecraft.level == null) {
            this.minecraft.crosshairPickEntity = null;
            return;
        }

        Vec3 eyePos = AeonisFreecam.INSTANCE.getCameraPos(tickDelta);
        Vec3 lookVec = player.getViewVector(tickDelta);
        double reach = AeonisFreecam.INSTANCE.isRemoteBreakEnabled() ? 128.0 : player.blockInteractionRange();
        double entityReach = Math.max(player.entityInteractionRange(), reach);
        Vec3 endPos = eyePos.add(lookVec.scale(entityReach));

        Entity closestEntity = null;
        double closestDistanceSq = entityReach * entityReach;
        AABB searchBox = new AABB(eyePos, endPos).inflate(1.0);

        for (Entity entity : this.minecraft.level.getEntities(player, searchBox, e -> !e.isSpectator() && e.isPickable())) {
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

        BlockHitResult blockHit = this.minecraft.level.clip(new ClipContext(
            eyePos, eyePos.add(lookVec.scale(reach)),
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            player
        ));

        double entityDistSq = closestEntity != null ? closestDistanceSq : Double.MAX_VALUE;
        double blockDistSq = blockHit.getType() != HitResult.Type.MISS ? eyePos.distanceToSqr(blockHit.getLocation()) : Double.MAX_VALUE;

        if (closestEntity != null && entityDistSq <= blockDistSq) {
            this.minecraft.crosshairPickEntity = closestEntity;
            AABB box = closestEntity.getBoundingBox().inflate(closestEntity.getPickRadius());
            java.util.Optional<Vec3> hitOpt = box.clip(eyePos, endPos);
            Vec3 hitPos = hitOpt.orElse(new Vec3(closestEntity.getX(), closestEntity.getY(), closestEntity.getZ()));
            this.minecraft.hitResult = new EntityHitResult(closestEntity, hitPos);
            return;
        }

        if (blockHit.getType() != HitResult.Type.MISS) {
            this.minecraft.crosshairPickEntity = null;
            this.minecraft.hitResult = blockHit;
            return;
        }

        this.minecraft.crosshairPickEntity = null;
        Direction missDir = Direction.getApproximateNearest(lookVec.x, lookVec.y, lookVec.z);
        BlockPos missPos = BlockPos.containing(endPos);
        this.minecraft.hitResult = BlockHitResult.miss(endPos, missDir, missPos);
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void aeonis$applyZoomFov(Camera camera, float tickDelta, boolean useConfigFov, CallbackInfoReturnable<Float> cir) {
        if (this.minecraft.player == null || this.minecraft.screen != null) {
            aeonis$zoomProgress = Math.max(0.0F, aeonis$zoomProgress - 0.25F);
            return;
        }

        float target = AeonisKeyBindings.INSTANCE.getZOOM_KEY().isDown() ? 1.0F : 0.0F;
        aeonis$zoomProgress += (target - aeonis$zoomProgress) * 0.35F;

        if (aeonis$zoomProgress < 0.001F) {
            return;
        }

        float baseFov = cir.getReturnValueF();
        float zoomScale = Mth.lerp(aeonis$zoomProgress, 1.0F, AEONIS_ZOOM_FOV_MULTIPLIER);
        cir.setReturnValue(Math.max(5.0F, baseFov * zoomScale));
    }
}
