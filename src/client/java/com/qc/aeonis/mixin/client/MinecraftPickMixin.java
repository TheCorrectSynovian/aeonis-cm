package com.qc.aeonis.mixin.client;

import com.qc.aeonis.client.AeonisFreecam;
import com.qc.aeonis.network.AeonisClientNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Custom crosshair targeting:
 * - Freecam pick uses the freecam camera position.
 * - When controlling a mob, exclude the controlled mob from being targeted.
 *
 * 26.2+: vanilla pick is now in `Minecraft.pick(float)`.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftPickMixin {
    @Shadow public HitResult hitResult;
    @Shadow public Entity crosshairPickEntity;
    @Shadow public net.minecraft.client.multiplayer.ClientLevel level;
    @Shadow public LocalPlayer player;

    @Shadow public abstract Entity getCameraEntity();

    @Inject(method = "pick", at = @At("HEAD"), cancellable = true, require = 0)
    private void aeonis$customPick(float partialTicks, CallbackInfo ci) {
        if (AeonisFreecam.INSTANCE.isEnabled()) {
            ci.cancel();
            aeonis$freecamPick(partialTicks);
            return;
        }

        if (!AeonisClientNetworking.INSTANCE.isControlling() || AeonisClientNetworking.INSTANCE.getControlledMobId() <= 0) {
            return;
        }

        ci.cancel();
        aeonis$controlledPick(partialTicks);
    }

    private void aeonis$controlledPick(float partialTicks) {
        if (this.player == null || this.level == null) {
            this.crosshairPickEntity = null;
            this.hitResult = null;
            return;
        }

        Entity cameraEntity = this.getCameraEntity();
        if (cameraEntity == null) {
            this.crosshairPickEntity = null;
            this.hitResult = null;
            return;
        }

        double blockReach = this.player.blockInteractionRange();
        double entityReach = Math.max(this.player.entityInteractionRange(), blockReach);

        Vec3 eyePos = cameraEntity.getEyePosition(partialTicks);
        Vec3 lookVec = cameraEntity.getViewVector(partialTicks);
        Vec3 blockEnd = eyePos.add(lookVec.scale(blockReach));
        Vec3 entityEnd = eyePos.add(lookVec.scale(entityReach));

        int controlledId = AeonisClientNetworking.INSTANCE.getControlledMobId();
        Entity closestEntity = null;
        double closestDistanceSq = entityReach * entityReach;

        AABB searchBox = cameraEntity.getBoundingBox().expandTowards(lookVec.scale(entityReach)).inflate(1.0);
        for (Entity entity : this.level.getEntities(cameraEntity, searchBox, e ->
            e.getId() != controlledId && !e.isSpectator() && e.isPickable()
        )) {
            AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
            java.util.Optional<Vec3> hitOpt = box.clip(eyePos, entityEnd);
            if (hitOpt.isPresent()) {
                double distSq = eyePos.distanceToSqr(hitOpt.get());
                if (distSq < closestDistanceSq) {
                    closestDistanceSq = distSq;
                    closestEntity = entity;
                }
            }
        }

        BlockHitResult blockHit = this.level.clip(new ClipContext(
            eyePos, blockEnd,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            cameraEntity
        ));

        double blockDistSq = blockHit.getType() != HitResult.Type.MISS ? eyePos.distanceToSqr(blockHit.getLocation()) : Double.MAX_VALUE;
        if (closestEntity != null && closestDistanceSq <= blockDistSq) {
            this.crosshairPickEntity = closestEntity;
            AABB box = closestEntity.getBoundingBox().inflate(closestEntity.getPickRadius());
            java.util.Optional<Vec3> hitOpt = box.clip(eyePos, entityEnd);
            Vec3 hitPos = hitOpt.orElse(null);
            if (hitPos == null) {
                hitPos = new Vec3(closestEntity.getX(), closestEntity.getY(), closestEntity.getZ());
            }
            this.hitResult = new EntityHitResult(closestEntity, hitPos);
            return;
        }

        if (blockHit.getType() != HitResult.Type.MISS) {
            this.crosshairPickEntity = null;
            this.hitResult = blockHit;
            return;
        }

        this.crosshairPickEntity = null;
        Direction missDir = Direction.getApproximateNearest(lookVec.x, lookVec.y, lookVec.z);
        BlockPos missPos = BlockPos.containing(blockEnd);
        this.hitResult = BlockHitResult.miss(blockEnd, missDir, missPos);
    }

    private void aeonis$freecamPick(float partialTicks) {
        if (this.player == null || this.level == null) {
            this.crosshairPickEntity = null;
            this.hitResult = null;
            return;
        }

        Vec3 eyePos = AeonisFreecam.INSTANCE.getCameraPos(partialTicks);
        Vec3 lookVec = this.player.getViewVector(partialTicks);
        double blockReach = AeonisFreecam.INSTANCE.isRemoteBreakEnabled() ? 128.0 : this.player.blockInteractionRange();
        double entityReach = Math.max(this.player.entityInteractionRange(), blockReach);
        Vec3 blockEnd = eyePos.add(lookVec.scale(blockReach));
        Vec3 entityEnd = eyePos.add(lookVec.scale(entityReach));

        Entity closestEntity = null;
        double closestDistanceSq = entityReach * entityReach;
        AABB searchBox = new AABB(eyePos, entityEnd).inflate(1.0);

        for (Entity entity : this.level.getEntities(this.player, searchBox, e -> !e.isSpectator() && e.isPickable())) {
            AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
            java.util.Optional<Vec3> hitOpt = box.clip(eyePos, entityEnd);
            if (hitOpt.isPresent()) {
                double distSq = eyePos.distanceToSqr(hitOpt.get());
                if (distSq < closestDistanceSq) {
                    closestDistanceSq = distSq;
                    closestEntity = entity;
                }
            }
        }

        BlockHitResult blockHit = this.level.clip(new ClipContext(
            eyePos, blockEnd,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            this.player
        ));

        double blockDistSq = blockHit.getType() != HitResult.Type.MISS ? eyePos.distanceToSqr(blockHit.getLocation()) : Double.MAX_VALUE;
        if (closestEntity != null && closestDistanceSq <= blockDistSq) {
            this.crosshairPickEntity = closestEntity;
            AABB box = closestEntity.getBoundingBox().inflate(closestEntity.getPickRadius());
            java.util.Optional<Vec3> hitOpt = box.clip(eyePos, entityEnd);
            Vec3 hitPos = hitOpt.orElse(null);
            if (hitPos == null) {
                hitPos = new Vec3(closestEntity.getX(), closestEntity.getY(), closestEntity.getZ());
            }
            this.hitResult = new EntityHitResult(closestEntity, hitPos);
            return;
        }

        if (blockHit.getType() != HitResult.Type.MISS) {
            this.crosshairPickEntity = null;
            this.hitResult = blockHit;
            return;
        }

        this.crosshairPickEntity = null;
        Direction missDir = Direction.getApproximateNearest(lookVec.x, lookVec.y, lookVec.z);
        BlockPos missPos = BlockPos.containing(blockEnd);
        this.hitResult = BlockHitResult.miss(blockEnd, missDir, missPos);
    }
}
