package com.qc.aeonis.mixin.client;

import com.qc.aeonis.network.AeonisClientNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Ghast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Handles client-side player tick updates for mob control
 * - Syncs mob position/rotation to player
 * - Handles flying mob controls
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    
    @Inject(method = "tick", at = @At("TAIL"))
    private void aeonis$onTick(CallbackInfo ci) {
        if (!AeonisClientNetworking.INSTANCE.isControlling()) {
            return;
        }
        
        int controlledMobId = AeonisClientNetworking.INSTANCE.getControlledMobId();
        if (controlledMobId <= 0) {
            return;
        }
        
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return;
        }
        
        LocalPlayer player = (LocalPlayer)(Object)this;
        
        // Find controlled mob
        Entity entity = client.level.getEntity(controlledMobId);
        if (!(entity instanceof Mob mob)) {
            return;
        }
        
        // NOTE: Do NOT sync mob position here - the server handles position sync
        // Only sync rotation for smoother visual feedback (position is authoritative from server)
        mob.setYRot(player.getYRot());
        mob.setXRot(player.getXRot());
        mob.yRotO = player.yRotO;
        mob.xRotO = player.xRotO;
        mob.setYBodyRot(player.getYRot());
        mob.setYHeadRot(player.getYRot());
        mob.yBodyRotO = player.yBodyRotO;
        mob.yHeadRotO = player.yHeadRotO;
        
        // Handle flying mobs - improved 3D flight controls
        if (canMobFly(mob)) {
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true; // Force flight mode for flying mobs
            
            // Ghast-like mobs: Float in air, SPACE to go up, SHIFT to go down
            if (isGhastLike(mob)) {
                double currY = player.getDeltaMovement().y;
                double targetY;

                if (client.options.keyJump.isDown()) {
                    targetY = 0.34;
                } else if (client.options.keyShift.isDown()) {
                    targetY = -0.26;
                } else {
                    targetY = -0.01; // slight hover drift
                }

                double smoothedY = currY + (targetY - currY) * 0.32;
                player.setDeltaMovement(
                    player.getDeltaMovement().x,
                    Math.max(-0.5, Math.min(smoothedY, 0.5)),
                    player.getDeltaMovement().z
                );
            } else {
                // Other flying mobs (Blaze, Bee, etc.) - smoothed vertical controls
                double currY = player.getDeltaMovement().y;
                double targetY;
                if (client.options.keyJump.isDown()) {
                    targetY = 0.24;
                } else if (client.options.keyShift.isDown()) {
                    targetY = -0.22;
                } else {
                    targetY = -0.015;
                }
                double smoothedY = currY + (targetY - currY) * 0.28;
                player.setDeltaMovement(
                    player.getDeltaMovement().x,
                    Math.max(-0.45, Math.min(smoothedY, 0.45)),
                    player.getDeltaMovement().z
                );
            }
        } else {
            // Reset flight for non-flying mobs (unless player is creative/spectator)
            if (!player.isCreative() && !player.isSpectator()) {
                player.getAbilities().mayfly = false;
                player.getAbilities().flying = false;
            }
        }
    }
    
    private static boolean canMobFly(Mob mob) {
        return isGhastLike(mob) ||
               mob instanceof net.minecraft.world.entity.monster.Blaze ||
               mob instanceof net.minecraft.world.entity.monster.Phantom ||
               mob instanceof net.minecraft.world.entity.monster.Vex ||
               mob instanceof net.minecraft.world.entity.animal.bee.Bee ||
               mob instanceof net.minecraft.world.entity.animal.allay.Allay ||
               mob instanceof net.minecraft.world.entity.animal.parrot.Parrot ||
               mob instanceof net.minecraft.world.entity.boss.wither.WitherBoss ||
               mob instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon ||
               mob instanceof net.minecraft.world.entity.monster.breeze.Breeze ||
               mob.getType() == EntityType.BAT ||
               mob.isNoGravity();
    }
    
    private static boolean isGhastLike(Mob mob) {
        // Check if it's a Ghast or any mob with "ghast" in its name (like Happy Ghast)
        if (mob instanceof Ghast) return true;
        try {
            String typePath = mob.getType().builtInRegistryHolder().key().identifier().getPath();
            return typePath.toLowerCase().contains("ghast");
        } catch (Exception e) {
            return false;
        }
    }
}
