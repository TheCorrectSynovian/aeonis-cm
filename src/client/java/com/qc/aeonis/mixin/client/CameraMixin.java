package com.qc.aeonis.mixin.client;

import com.qc.aeonis.network.AeonisClientNetworking;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Camera at MOB's eye position when controlling
 * Also handles third-person camera distance for large mobs
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    
    @Shadow
    protected abstract void setPosition(double x, double y, double z);
    
    @Shadow
    protected abstract void move(float distanceOffset, float verticalOffset, float horizontalOffset);
    
    @Inject(method = "setup", at = @At("TAIL"))
    private void aeonis$adjustCameraForMob(Level area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float partialTicks, CallbackInfo ci) {
        if (!AeonisClientNetworking.INSTANCE.isControlling() || AeonisClientNetworking.INSTANCE.getControlledMobId() <= 0) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        
        // Find controlled mob
        int mobId = AeonisClientNetworking.INSTANCE.getControlledMobId();
        LivingEntity mob = null;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity.getId() == mobId && entity instanceof LivingEntity living) {
                mob = living;
                break;
            }
        }
        
        if (mob == null) return;
        
        // Get MOB's interpolated position
        Vec3 mobPos = mob.getPosition(partialTicks);
        float mobEyeHeight = mob.getEyeHeight();
        
        // FIRST PERSON: Camera at MOB's eye position
        if (!thirdPerson) {
            this.setPosition(mobPos.x, mobPos.y + (double)mobEyeHeight, mobPos.z);
        } else {
            // THIRD PERSON: Move camera further back for large mobs
            float extraDistance = getExtraCameraDistance(mob);
            if (extraDistance > 0) {
                // Move camera backward by extra distance
                this.move(-extraDistance, 0, 0);
            }
        }
    }
    
    /**
     * Calculate extra camera distance for large mobs
     */
    private static float getExtraCameraDistance(LivingEntity mob) {
        // Ghast and similar large flying mobs
        if (isGhastLike(mob)) {
            return 8.0f; // Much further back for Ghast (they're huge)
        }
        
        // Ender Dragon
        if (mob.getType() == EntityType.ENDER_DRAGON) {
            return 12.0f; // Even further for dragon
        }
        
        // Wither
        if (mob.getType() == EntityType.WITHER) {
            return 6.0f;
        }
        
        // Ravager, Iron Golem, and other large mobs
        if (mob.getType() == EntityType.RAVAGER) {
            return 4.0f;
        }
        
        if (mob.getType() == EntityType.IRON_GOLEM) {
            return 3.0f;
        }
        
        // Giant (if it exists)
        if (mob.getType() == EntityType.GIANT) {
            return 10.0f;
        }
        
        // For any other mob, base it on bounding box size
        float mobWidth = mob.getBbWidth();
        float mobHeight = mob.getBbHeight();
        float size = Math.max(mobWidth, mobHeight);
        
        if (size > 3.0f) {
            return size * 1.5f;
        } else if (size > 2.0f) {
            return size * 0.8f;
        }
        
        return 0; // No extra distance for normal-sized mobs
    }
    
    private static boolean isGhastLike(LivingEntity mob) {
        if (mob instanceof Ghast) return true;
        try {
            String typePath = mob.getType().builtInRegistryHolder().key().identifier().getPath();
            return typePath.toLowerCase().contains("ghast");
        } catch (Exception e) {
            return false;
        }
    }
}
