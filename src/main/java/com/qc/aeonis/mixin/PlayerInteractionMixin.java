package com.qc.aeonis.mixin;

import com.qc.aeonis.AeonisPossession;
import com.qc.aeonis.network.AeonisNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerInteractionMixin {
    @Inject(method = "useItemOn(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;", at = @At("HEAD"), cancellable = true)
    private void preventBlockPlace(InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        Player player = (Player)(Object)this;
        if (player instanceof ServerPlayer) {
            if (AeonisPossession.INSTANCE.isPlayerInPossessionMode(player.getUUID()) && AeonisNetworking.INSTANCE.getControlledEntityId(player.getUUID()) == null) {
                cir.setReturnValue(InteractionResult.PASS);
            }
        }
    }

    @Inject(method = "use(Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;", at = @At("HEAD"), cancellable = true)
    private void preventItemUse(InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        Player player = (Player)(Object)this;
        if (player instanceof ServerPlayer) {
            if (AeonisPossession.INSTANCE.isPlayerInPossessionMode(player.getUUID()) && AeonisNetworking.INSTANCE.getControlledEntityId(player.getUUID()) == null) {
                cir.setReturnValue(InteractionResult.PASS);
            }
        }
    }
}