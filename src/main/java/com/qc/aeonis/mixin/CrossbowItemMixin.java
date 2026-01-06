package com.qc.aeonis.mixin;

import com.qc.aeonis.network.AeonisNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Allows pillagers and piglins to properly use crossbows from their position.
 */
@Mixin(CrossbowItem.class)
public class CrossbowItemMixin {
    @Inject(method = "use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;", at = @At("HEAD"), cancellable = true)
    private void onUse(Level world, Player user, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (user instanceof ServerPlayer player && AeonisNetworking.INSTANCE.isPlayerTransformed(player.getUUID())) {
            Integer controlledId = AeonisNetworking.INSTANCE.getControlledEntityId(player.getUUID());
            if (controlledId != null) {
                var entity = player.level().getEntity(controlledId);
                if (entity instanceof Mob mob && isCrossbowMob(mob)) {
                    ItemStack stack = user.getItemInHand(hand);
                    
                    // If crossbow is charged, fire it
                    if (CrossbowItem.isCharged(stack)) {
                        // Continue to normal behavior which will fire
                        return;
                    }
                    
                    // Check if player has arrows
                    if (user.getProjectile(stack).isEmpty()) {
                        return;
                    }
                    
                    // Start charging
                    user.startUsingItem(hand);
                    cir.setReturnValue(InteractionResult.CONSUME);
                }
            }
        }
    }

    @Inject(method = "releaseUsing(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;I)Z", at = @At("HEAD"), cancellable = true)
    private void onStoppedUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks, CallbackInfoReturnable<Boolean> cir) {
        if (user instanceof ServerPlayer player && AeonisNetworking.INSTANCE.isPlayerTransformed(player.getUUID())) {
            Integer controlledId = AeonisNetworking.INSTANCE.getControlledEntityId(player.getUUID());
            if (controlledId != null) {
                var entity = player.level().getEntity(controlledId);
                if (entity instanceof Mob mob && isCrossbowMob(mob)) {
                    // If no ammo, cancel
                    if (user.getProjectile(stack).isEmpty()) {
                        cir.setReturnValue(false);
                    }
                    // Otherwise let normal crossbow loading continue
                }
            }
        }
    }

    private static boolean isCrossbowMob(Mob mob) {
        return mob instanceof Pillager
            || mob.getType() == EntityType.PIGLIN;
    }
}
