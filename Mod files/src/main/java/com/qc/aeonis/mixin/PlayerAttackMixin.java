package com.qc.aeonis.mixin;

import com.qc.aeonis.mixin.BeeEntityAccessor;
import com.qc.aeonis.mixin.IronGolemEntityAccessor;
import com.qc.aeonis.network.AeonisNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Mixin(Player.class)
public abstract class PlayerAttackMixin {
    @Unique
    private static final Set<Integer> aeonis$beesHaveStung = new HashSet<>();
    
    @Unique
    private static final Map<UUID, Long> aeonis$attackCooldowns = new HashMap<>();

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void onAttack(Entity target, CallbackInfo ci) {
        Player player = (Player)(Object)this;
        
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        
        if (!AeonisNetworking.INSTANCE.isPlayerTransformed(player.getUUID())) {
            return;
        }
        
        Integer controlledId = AeonisNetworking.INSTANCE.getControlledEntityId(player.getUUID());
        if (controlledId == null) {
            ci.cancel();
            return;
        }
        
        // Prevent attacking your own mob
        if (target.getId() == controlledId) {
            ci.cancel();
            return;
        }
        
        // Attack cooldown (300ms)
        long now = System.currentTimeMillis();
        Long lastAttack = aeonis$attackCooldowns.get(player.getUUID());
        if (lastAttack != null && now - lastAttack < 300L) {
            ci.cancel();
            return;
        }
        aeonis$attackCooldowns.put(player.getUUID(), now);
        
        ci.cancel(); // Cancel normal attack, we'll do custom damage
        
        Entity entity = player.level().getEntity(controlledId);
        if (!(entity instanceof Mob controlledMob)) {
            return;
        }
        
        ServerLevel world = (ServerLevel) player.level();
        
        // Calculate mob's melee reach based on its size
        double mobReach = getMobMeleeReach(controlledMob);
        
        // Find target entity from MOB's position using MOB's look direction
        Vec3 mobEyePos = controlledMob.getEyePosition();
        Vec3 lookVec = controlledMob.getViewVector(1.0f);
        Vec3 endPos = mobEyePos.add(lookVec.scale(mobReach));
        
        // Find entities in the attack path from the MOB
        AABB searchBox = controlledMob.getBoundingBox().expandTowards(lookVec.scale(mobReach)).inflate(1.0);
        List<Entity> nearbyEntities = world.getEntities(controlledMob, searchBox, 
            e -> e != controlledMob && e != player && e instanceof LivingEntity && e.isAlive());
        
        // Find the closest entity in the mob's line of sight
        LivingEntity livingTarget = null;
        double closestDistSq = mobReach * mobReach;
        
        for (Entity e : nearbyEntities) {
            AABB box = e.getBoundingBox().inflate(e.getPickRadius());
            Optional<Vec3> hitOpt = box.clip(mobEyePos, endPos);
            if (hitOpt.isPresent()) {
                double distSq = mobEyePos.distanceToSqr(hitOpt.get());
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    livingTarget = (LivingEntity) e;
                }
            }
        }
        
        // If no entity found via raycast, check if the clicked target is within mob's reach
        if (livingTarget == null && target instanceof LivingEntity lt) {
            double distToTarget = controlledMob.distanceTo(target);
            if (distToTarget <= mobReach + 1.0) {
                livingTarget = lt;
            }
        }
        
        if (livingTarget == null) {
            // Play swing/miss sound
            controlledMob.playSound(SoundEvents.PLAYER_ATTACK_NODAMAGE, 1.0f, 1.0f);
            return;
        }
        
        // Get base mob damage from attributes (vanilla mob damage)
        float damage;
        try {
            damage = (float) controlledMob.getAttributeValue(Attributes.ATTACK_DAMAGE);
        } catch (Exception e) {
            damage = 2.0f; // Default fallback damage
        }
        
        // Special mob attack mechanics
        boolean isIronGolem = controlledMob instanceof IronGolem;
        boolean isRavager = controlledMob instanceof Ravager;
        boolean isBee = controlledMob instanceof Bee;
        boolean isWarden = controlledMob.getType() == EntityType.WARDEN;
        
        // === BEE: Single sting with poison ===
        if (isBee) {
            if (aeonis$beesHaveStung.contains(controlledMob.getId())) {
                return; // Already stung!
            }
            aeonis$beesHaveStung.add(controlledMob.getId());
            
            Bee bee = (Bee) controlledMob;
            damage = 1.0f;
            livingTarget.invulnerableTime = 0;
            livingTarget.hurtTime = 0;
            
            // Apply damage and poison
            livingTarget.hurt(world.damageSources().sting(bee), damage);
            livingTarget.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
            
            // Play sting sound
            bee.playSound(SoundEvents.BEE_STING, 1.0f, 1.0f);
            ((BeeEntityAccessor) bee).invokeSetHasStung(true);
            return;
        }
        
        // === IRON GOLEM: 15 damage + launch knockback ===
        if (isIronGolem) {
            IronGolem golem = (IronGolem) controlledMob;
            damage = 15.0f;
            ((IronGolemEntityAccessor) golem).setAttackTicksLeft(10);
            world.broadcastEntityEvent(controlledMob, (byte) 4);
        }
        
        // === RAVAGER: 12 damage + knockback + roar ===
        if (isRavager) {
            Ravager ravager = (Ravager) controlledMob;
            damage = 12.0f;
            world.broadcastEntityEvent(ravager, (byte) 4);
            ravager.playSound(SoundEvents.RAVAGER_ATTACK, 1.0f, 1.0f);
        }
        
        // === WARDEN: 30 damage + sonic boom sound ===
        if (isWarden) {
            damage = 30.0f;
            world.broadcastEntityEvent(controlledMob, (byte) 4);
            controlledMob.playSound(SoundEvents.WARDEN_ATTACK_IMPACT, 1.0f, 1.0f);
        }
        
        // Reset target invulnerability to allow hit
        livingTarget.invulnerableTime = 0;
        livingTarget.hurtTime = 0;
        
        // Deal damage from the controlled mob
        DamageSource damageSource = world.damageSources().mobAttack(controlledMob);
        livingTarget.hurt(damageSource, damage);
        
        // Play generic attack sound for mobs without special sounds
        if (!isIronGolem && !isRavager && !isWarden && !isBee) {
            controlledMob.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 1.0f);
        }
        
        // Special knockback for Iron Golem (launch target!)
        if (isIronGolem) {
            IronGolem golem = (IronGolem) controlledMob;
            double dx = livingTarget.getX() - golem.getX();
            double dz = livingTarget.getZ() - golem.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > 0) {
                dx /= dist;
                dz /= dist;
            }
            // Launch them!
            livingTarget.push(dx * 1.2, 0.9, dz * 1.2);
            livingTarget.hurtMarked = true;
        }
        
        // Knockback for Ravager
        if (isRavager) {
            Ravager ravager = (Ravager) controlledMob;
            double dx = livingTarget.getX() - ravager.getX();
            double dz = livingTarget.getZ() - ravager.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > 0) {
                dx /= dist;
                dz /= dist;
            }
            livingTarget.push(dx * 0.5, 0.5, dz * 0.5);
            livingTarget.hurtMarked = true;
        }
        
        // Make target angry at the controlled mob
        if (livingTarget instanceof Mob mobTarget) {
            mobTarget.setTarget(controlledMob);
        }
        livingTarget.setLastHurtByMob(controlledMob);
    }
    
    /**
     * Get the melee attack reach for a mob based on its type and size
     * Larger mobs have longer reach
     */
    @Unique
    private static double getMobMeleeReach(Mob mob) {
        // Special cases for specific mobs
        if (mob instanceof IronGolem) return 4.0;
        if (mob instanceof Ravager) return 4.5;
        if (mob.getType() == EntityType.WARDEN) return 5.0;
        if (mob.getType() == EntityType.ENDER_DRAGON) return 8.0;
        if (mob instanceof Ghast || isGhastLike(mob)) return 6.0; // Ghasts don't melee but just in case
        if (mob.getType() == EntityType.GIANT) return 8.0;
        if (mob.getType() == EntityType.WITHER) return 5.0;
        if (mob instanceof Bee) return 1.5;
        
        // Default: base reach on mob's bounding box width
        float width = mob.getBbWidth();
        float height = mob.getBbHeight();
        double baseReach = Math.max(width, height) + 1.5;
        
        // Clamp between reasonable values
        return Math.max(2.0, Math.min(baseReach, 6.0));
    }
    
    @Unique
    private static boolean isGhastLike(Mob mob) {
        try {
            String typePath = mob.getType().builtInRegistryHolder().key().location().getPath();
            return typePath.toLowerCase().contains("ghast");
        } catch (Exception e) {
            return false;
        }
    }
}