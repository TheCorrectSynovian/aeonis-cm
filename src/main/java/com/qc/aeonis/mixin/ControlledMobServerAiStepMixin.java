package com.qc.aeonis.mixin;

import com.qc.aeonis.network.AeonisNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.cubemob.Slime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2+: `LivingEntity.aiStep` only performs `travel(input)` when `isEffectiveAi()` is true.
 * Using `Mob#setNoAi(true)` makes many mobs completely immobile. Instead of toggling NoAI,
 * suppress server-side AI processing while the mob is controlled by a player.
 */
@Mixin(Mob.class)
public class ControlledMobServerAiStepMixin {
    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void aeonis$cancelServerAiWhenControlled(CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (!(self.level() instanceof ServerLevel)) {
            return;
        }
        if (!AeonisNetworking.INSTANCE.isEntityControlled(self.getId())) {
            return;
        }
        // Preserve vanilla movement controllers for cube-style mobs and the Ender Dragon.
        if (self instanceof EnderDragon || isCubeStyleMob(self)) {
            return;
        }
        ci.cancel();
    }

    private static boolean isCubeStyleMob(Mob entity) {
        if (entity instanceof Slime) {
            return true;
        }
        try {
            return "sulfur_cube".equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath());
        } catch (Exception ignored) {
            return false;
        }
    }
}
