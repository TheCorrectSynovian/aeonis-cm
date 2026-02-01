package com.qc.aeonis.mixin;

import com.qc.aeonis.network.AeonisNetworking;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnderMan.class)
public abstract class EndermanHeldBlockMixin {
    @Shadow public abstract BlockState getCarriedBlock();
    @Shadow public abstract void setCarriedBlock(BlockState state);

    @Inject(method = "getCarriedBlock", at = @At("HEAD"), cancellable = true)
    private void onGetCarriedBlock(CallbackInfoReturnable<BlockState> cir) {
        EnderMan enderman = (EnderMan)(Object)this;
        // Server-side: check players controlling this enderman and return their held block
        if (enderman.level() != null && !enderman.level().isClientSide()) {
            for (var player : enderman.level().players()) {
                try {
                    var id = AeonisNetworking.INSTANCE.getControlledEntityId(player.getUUID());
                    if (id != null && id == enderman.getId()) {
                        ItemStack held = player.getMainHandItem();
                        if (held.getItem() instanceof BlockItem) {
                            BlockItem bi = (BlockItem)held.getItem();
                            cir.setReturnValue(bi.getBlock().defaultBlockState());
                        } else {
                            cir.setReturnValue(null);
                        }
                        return;
                    }
                } catch (Throwable ignored) {}
            }
        }
    }
}