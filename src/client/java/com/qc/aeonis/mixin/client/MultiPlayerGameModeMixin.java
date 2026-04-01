package com.qc.aeonis.mixin.client;

import com.qc.aeonis.client.AeonisFreecam;
import com.qc.aeonis.network.AeonisClientNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client interaction hooks.
 * Keep vanilla block/entity interaction intact while transformed.
 */
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private ClientPacketListener connection;
    
    /**
     * Only intercept block destroy in freecam remote-break mode.
     * In normal transformed gameplay, vanilla behavior should run unmodified.
     */
    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void aeonis$allowBlockBreakingOnLongPress(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (AeonisFreecam.INSTANCE.isRemoteBreakEnabled()) {
            if (this.minecraft.player == null || this.minecraft.level == null) {
                cir.setReturnValue(false);
                return;
            }
            if (!this.minecraft.level.getWorldBorder().isWithinBounds(pos)) {
                cir.setReturnValue(false);
                return;
            }
            this.connection.send(new ServerboundPlayerActionPacket(Action.START_DESTROY_BLOCK, pos, direction));
            cir.setReturnValue(true);
            return;
        }
    }
    
    /**
     * Only intercept continuation in freecam remote-break mode.
     */
    @Inject(method = "continueDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void aeonis$allowContinueBlockBreaking(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (AeonisFreecam.INSTANCE.isRemoteBreakEnabled()) {
            if (this.minecraft.player == null || this.minecraft.level == null) {
                cir.setReturnValue(false);
                return;
            }
            this.connection.send(new ServerboundPlayerActionPacket(Action.START_DESTROY_BLOCK, pos, direction));
            this.connection.send(new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, pos, direction));
            cir.setReturnValue(true);
            return;
        }
    }

    @Inject(method = "stopDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void aeonis$freecamStopDestroy(CallbackInfo ci) {
        if (!AeonisFreecam.INSTANCE.isRemoteBreakEnabled()) {
            return;
        }
        if (this.minecraft.player == null || this.minecraft.level == null) {
            ci.cancel();
            return;
        }

        BlockPos pos = BlockPos.ZERO;
        Direction direction = Direction.DOWN;
        if (this.minecraft.hitResult instanceof BlockHitResult blockHit && this.minecraft.hitResult.getType() != HitResult.Type.MISS) {
            pos = blockHit.getBlockPos();
            direction = blockHit.getDirection();
        }
        this.connection.send(new ServerboundPlayerActionPacket(Action.ABORT_DESTROY_BLOCK, pos, direction));
        ci.cancel();
    }
}
