package com.qc.aeonis.mixin;

import com.qc.aeonis.network.AeonisNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public class PlayerDamageMixin {
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void onDamage(net.minecraft.server.level.ServerLevel world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (!AeonisNetworking.INSTANCE.isPlayerTransformed(player.getUUID())) {
            return;
        }

        String msgId = source.getMsgId();
        if (msgId != null && (msgId.equals("inWall") || msgId.equals("cramming"))) {
            cir.setReturnValue(false);
            return;
        }
        
        Integer controlledId = AeonisNetworking.INSTANCE.getControlledEntityId(player.getUUID());
        if (controlledId == null) return;
        
        Entity entity = player.level().getEntity(controlledId);
        if (!(entity instanceof Mob mob)) return;
        
        // === SUFFOCATION/IN_WALL damage: handled above for all transformed players ===
        
        // === FALL damage: Block for flying mobs ===
        if (source.is(DamageTypeTags.IS_FALL)) {
            if (mob instanceof Ghast || mob instanceof Blaze || mob instanceof Phantom ||
                mob instanceof Vex || mob instanceof Bee || mob instanceof Allay ||
                mob instanceof Parrot || mob.getType() == EntityType.WITHER ||
                mob.getType() == EntityType.ENDER_DRAGON || mob instanceof Breeze ||
                mob.isNoGravity()) {
                cir.setReturnValue(false);
                return;
            }
        }
        
        // === FIRE damage: Block for fire-immune mobs ===
        if (source.is(DamageTypeTags.IS_FIRE)) {
            if (mob.fireImmune()) {
                cir.setReturnValue(false);
                return;
            }
        }
        
        // === DROWNING: Block for water mobs ===
        if (source.is(DamageTypeTags.IS_DROWNING)) {
            if (mob instanceof WaterAnimal || mob instanceof Guardian || mob instanceof Drowned) {
                cir.setReturnValue(false);
                return;
            }
        }
        
        // Redirect all other damage to the controlled mob
        if (mob instanceof LivingEntity livingMob) {
            livingMob.hurt(source, amount);
            
            // Apply knockback to player from attacker direction
            Entity attacker = source.getEntity();
            if (attacker != null) {
                double dx = attacker.getX() - player.getX();
                double dz = attacker.getZ() - player.getZ();
                player.knockback(0.4, dx, dz);
                player.hurtMarked = true;
            }
            
            // Set damage animation
            player.hurtTime = 10;
            player.hurtDuration = 10;
        }
        
        cir.setReturnValue(false);
    }
}
