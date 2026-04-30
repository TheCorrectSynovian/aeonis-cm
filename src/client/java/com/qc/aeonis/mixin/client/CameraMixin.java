package com.qc.aeonis.mixin.client;

import com.qc.aeonis.client.AeonisFreecam;
import com.qc.aeonis.network.AeonisClientNetworking;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Ghast;
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
    
    // 26.2+: camera alignment is handled in `alignWithEntity(partialTicks)` instead of `setup(...)`.
    @Inject(method = "alignWithEntity", at = @At("TAIL"), require = 0)
    private void aeonis$adjustCameraForMob(float partialTicks, CallbackInfo ci) {
        if (AeonisFreecam.INSTANCE.isEnabled()) {
            Vec3 freecamPos = AeonisFreecam.INSTANCE.getCameraPos(partialTicks);
            this.setPosition(freecamPos.x, freecamPos.y, freecamPos.z);
            return;
        }

        if (!AeonisClientNetworking.INSTANCE.isControlling() || AeonisClientNetworking.INSTANCE.getControlledMobId() <= 0) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        
        // Find controlled mob directly by id (more reliable than render-entity iteration)
        int mobId = AeonisClientNetworking.INSTANCE.getControlledMobId();
        Entity controlledEntity = mc.level.getEntity(mobId);
        LivingEntity mob = controlledEntity instanceof LivingEntity living ? living : null;
        
        if (mob == null) return;
        
        // Use true interpolated eye position for accurate first-person POV.
        Vec3 mobEyePos = mob.getEyePosition(partialTicks);
        boolean sulfurCube = isSulfurCube(mob);
        boolean genericCube = isCubeMob(mob);
        
        boolean thirdPerson = !mc.options.getCameraType().isFirstPerson();

        // FIRST PERSON: Camera at MOB's eye position
        if (!thirdPerson) {
            if (genericCube) {
                // Cube mobs look better from a stable center-biased camera.
                double y = mob.getY() + mob.getBbHeight() * 0.72;
                if (sulfurCube) {
                    y += Math.sin((mob.tickCount + partialTicks) * 0.35) * 0.045;
                }
                this.setPosition(mob.getX(), y, mob.getZ());
            } else {
                this.setPosition(mobEyePos.x, mobEyePos.y, mobEyePos.z);
            }
        } else {
            // THIRD PERSON: anchor camera to the controlled mob, then apply a reasonable distance.
            if (genericCube) {
                double y = mob.getY() + mob.getBbHeight() * 0.72;
                if (sulfurCube) {
                    y += Math.sin((mob.tickCount + partialTicks) * 0.35) * 0.045;
                }
                this.setPosition(mob.getX(), y, mob.getZ());
            } else {
                this.setPosition(mobEyePos.x, mobEyePos.y, mobEyePos.z);
            }

            float baseDistance = 4.0f;
            try {
                baseDistance = (float) mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.CAMERA_DISTANCE);
            } catch (Exception ignored) {
                // fallback to default
            }
            float scale = 1.0f;
            try {
                scale = mob.getScale();
            } catch (Exception ignored) {
                // ignore
            }
            this.move(-(baseDistance * scale), 0, 0);

            // Move camera further back for large mobs
            float extraDistance = getExtraCameraDistance(mob);
            if (extraDistance > 0) {
                this.move(-extraDistance, 0, 0);
            }
            if (genericCube) {
                float cubeBack = sulfurCube ? 3.4f : 2.6f;
                float cubeUp = sulfurCube ? 0.38f : 0.28f;
                this.move(-cubeBack, cubeUp, 0);
            }
        }
    }
    
    /**
     * Calculate extra camera distance for large mobs
     */
    private static float getExtraCameraDistance(LivingEntity mob) {
        if (isCubeMob(mob)) {
            return 0;
        }
        // Ghast and similar large flying mobs
        if (isGhastLike(mob)) {
            return 8.0f; // Much further back for Ghast (they're huge)
        }
        
        // Ender Dragon
        if (mob.getType() == EntityTypes.ENDER_DRAGON) {
            return 12.0f; // Even further for dragon
        }
        
        // Wither
        if (mob.getType() == EntityTypes.WITHER) {
            return 6.0f;
        }
        
        // Ravager, Iron Golem, and other large mobs
        if (mob.getType() == EntityTypes.RAVAGER) {
            return 4.0f;
        }
        
        if (mob.getType() == EntityTypes.IRON_GOLEM) {
            return 3.0f;
        }
        
        // Giant (if it exists)
        if (mob.getType() == EntityTypes.GIANT) {
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

    private static boolean isCubeMob(LivingEntity mob) {
        try {
            String typePath = mob.getType().builtInRegistryHolder().key().identifier().getPath();
            return typePath.contains("cube");
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isSulfurCube(LivingEntity mob) {
        try {
            String typePath = mob.getType().builtInRegistryHolder().key().identifier().getPath();
            if (!"sulfur_cube".equals(typePath)) {
                return false;
            }
            return !mob.getItemBySlot(EquipmentSlot.BODY).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}
