package com.qc.aeonis.mixin;

import com.qc.aeonis.AeonisPossession;
import com.qc.aeonis.network.AeonisNetworking;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerDropItemMixin {
    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void preventItemDrop(ItemStack stack, boolean throwRandomly, boolean retainOwnership, CallbackInfoReturnable<ItemEntity> cir) {
        Player player = (Player)(Object)this;
        if (AeonisPossession.INSTANCE.isPlayerInPossessionMode(player.getUUID()) && AeonisNetworking.INSTANCE.getControlledEntityId(player.getUUID()) == null) {
            cir.setReturnValue(null);
        }
    }
}