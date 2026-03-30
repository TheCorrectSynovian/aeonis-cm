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
 * Mixin to handle attack behavior when player is controlling a mob.
 * Short left-click = melee attack by controlled mob (handled via MobControlPayload)
 * Long left-click = block breaking by player (allow vanilla behavior)
 */
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private ClientPacketListener connection;
    
    /**
     * Cancel entity attacks while controlling - melee is handled via custom packet
     */
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void aeonis$cancelAttackWhileControlling(Player player, Entity target, CallbackInfo ci) {
        // If player is controlling a mob, cancel the vanilla entity attack
        // Our custom melee attack handling is done via MobControlPayload on short click
        if (AeonisClientNetworking.INSTANCE.isControlling()) {
            ci.cancel();
        }
    }
    
    /**
     * Allow block breaking when long-pressing left click while controlling a mob
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

        // If controlling and NOT in block breaking mode (short click), cancel block breaking start
        if (AeonisClientNetworking.INSTANCE.isControlling() && !AeonisClientNetworking.INSTANCE.isBreakingBlock()) {
            cir.setReturnValue(false);
        }
        // If isBreakingBlock() is true, allow vanilla block breaking to proceed
    }
    
    /**
     * Allow block breaking continuation when in block breaking mode
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

        // Only allow continuing block break if we're in block breaking mode
        if (AeonisClientNetworking.INSTANCE.isControlling() && !AeonisClientNetworking.INSTANCE.isBreakingBlock()) {
            cir.setReturnValue(false);
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
