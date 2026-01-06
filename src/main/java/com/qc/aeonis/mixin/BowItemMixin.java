package com.qc.aeonis.mixin;

import com.qc.aeonis.network.AeonisNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.Stray;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Allows skeleton, stray, piglin, and bogged mobs to properly shoot arrows from their position.
 */
@Mixin(BowItem.class)
public class BowItemMixin {
    @Inject(method = "use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;", at = @At("HEAD"), cancellable = true)
    private void onUse(Level world, Player user, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (user instanceof ServerPlayer player && AeonisNetworking.INSTANCE.isPlayerTransformed(player.getUUID())) {
            Integer controlledId = AeonisNetworking.INSTANCE.getControlledEntityId(player.getUUID());
            if (controlledId != null) {
                var entity = player.level().getEntity(controlledId);
                if (entity instanceof Mob mob && isArcherMob(mob)) {
                    // Allow bow usage, continue normal behavior
                    ItemStack stack = user.getItemInHand(hand);
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
                if (entity instanceof Mob mob && isArcherMob(mob)) {
                    cir.setReturnValue(true);
                    
                    int useTime = stack.getUseDuration(user) - remainingUseTicks;
                    float pullProgress = BowItem.getPowerForTime(useTime);
                    
                    if (pullProgress < 0.1f) {
                        return;
                    }
                    
                    if (!world.isClientSide()) {
                        ServerLevel serverWorld = (ServerLevel) world;
                        
                        // Create arrow from mob's position
                        ArrowItem arrowItem = (ArrowItem) Items.ARROW;
                        ItemStack arrowStack = new ItemStack(Items.ARROW);
                        Arrow arrow = new Arrow(world, mob, arrowStack, stack);
                        
                        // Add slowness for stray/bogged
                        if (mob.getType() == EntityType.STRAY || mob.getType() == EntityType.BOGGED) {
                            arrow.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 600, 0));
                        }
                        if (mob.getType() == EntityType.BOGGED) {
                            arrow.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
                        }
                        
                        // Position at mob
                        arrow.setPos(mob.getX(), mob.getEyeY() - 0.1, mob.getZ());
                        arrow.shootFromRotation(mob, mob.getXRot(), mob.getYRot(), 0.0f, pullProgress * 3.0f, 1.0f);
                        
                        if (pullProgress == 1.0f) {
                            arrow.setCritArrow(true);
                        }
                        
                        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                        world.addFreshEntity(arrow);
                    }
                    
                    // Play sound
                    world.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                        SoundEvents.ARROW_SHOOT, SoundSource.NEUTRAL,
                        1.0f, 1.0f / (world.random.nextFloat() * 0.4f + 1.2f) + pullProgress * 0.5f);
                }
            }
        }
    }

    private static boolean isArcherMob(Mob mob) {
        return mob instanceof AbstractSkeleton 
            || mob instanceof Piglin
            || mob.getType() == EntityType.BOGGED
            || mob.getType() == EntityType.STRAY;
    }
}
