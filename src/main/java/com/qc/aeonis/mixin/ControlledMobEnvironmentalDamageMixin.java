package com.qc.aeonis.mixin;

import com.qc.aeonis.network.AeonisNetworking;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class ControlledMobEnvironmentalDamageMixin {
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void aeonis$ignoreControlledMobCollisionAndFallDamage(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide()) {
            return;
        }
        if (!AeonisNetworking.INSTANCE.isEntityControlled(self.getId())) {
            return;
        }

        // Prevent transformed-control desync damage from cramped hitboxes and forced sync.
        String msgId = source.getMsgId();
        if (msgId != null && (msgId.equals("inWall") || msgId.equals("cramming") || msgId.equals("flyIntoWall"))) {
            cir.setReturnValue(false);
            return;
        }

        if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING)) {
            cir.setReturnValue(false);
            return;
        }

        // Small mobs are extra sensitive to sync collisions, so protect them from contact damage.
        boolean smallMob = self instanceof AbstractCubeMob cubeMob && cubeMob.getSize() <= 1;
        if (smallMob && source.is(DamageTypeTags.IS_FIRE)) {
            cir.setReturnValue(false);
        }
    }
}
