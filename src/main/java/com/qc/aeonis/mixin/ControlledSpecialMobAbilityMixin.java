package com.qc.aeonis.mixin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.qc.aeonis.network.AeonisNetworking;
import com.qc.aeonis.network.MobControlPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

@Mixin(Mob.class)
public abstract class ControlledSpecialMobAbilityMixin {
    private static final Map<UUID, Integer> ENDERMAN_BLINK_COOLDOWN = new HashMap<>();
    private static final Map<UUID, Integer> VILLAGER_AURA_COOLDOWN = new HashMap<>();
    private static final Map<UUID, Integer> WARDEN_ROAR_COOLDOWN = new HashMap<>();

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void aeonis$applySpecialControlledMobAbilities(CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (!(self.level() instanceof ServerLevel level)) {
            return;
        }

        UUID controller = AeonisNetworking.INSTANCE.getControllingPlayerUuid(self.getId());
        if (controller == null) {
            return;
        }

        MobControlPayload input = AeonisNetworking.INSTANCE.getLatestControlInput(controller);
        if (input == null) {
            return;
        }

        tickCooldown(ENDERMAN_BLINK_COOLDOWN, controller);
        tickCooldown(VILLAGER_AURA_COOLDOWN, controller);
        tickCooldown(WARDEN_ROAR_COOLDOWN, controller);

        if (self instanceof EnderMan enderMan) {
            handleEndermanBlink(level, controller, enderMan, input);
        } else if (self instanceof Creeper creeper) {
            // Creeper can be primed manually while controlled.
            if (input.getSneak() && !creeper.isIgnited()) {
                creeper.ignite();
            }
        } else if (self instanceof EnderDragon dragon) {
            handleDragonControl(dragon, input);
        } else if (self instanceof Warden warden) {
            handleWardenRoar(level, controller, warden, input);
        } else if (self instanceof Villager villager) {
            handleVillagerAura(level, controller, villager, input);
        }
    }

    private static void handleEndermanBlink(ServerLevel level, UUID controller, EnderMan enderMan, MobControlPayload input) {
        if (!input.getTeleport()) {
            return;
        }
        if (ENDERMAN_BLINK_COOLDOWN.getOrDefault(controller, 0) > 0) {
            return;
        }
        ENDERMAN_BLINK_COOLDOWN.put(controller, 10);

        Vec3 look = enderMan.getLookAngle().normalize();
        double x = enderMan.getX() + look.x * 10.0;
        double y = enderMan.getY() + Math.max(-2.0, look.y * 6.0);
        double z = enderMan.getZ() + look.z * 10.0;
        enderMan.teleportTo(x, y, z);
        level.playSound(null, enderMan.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0f, 1.0f);
    }

    private static void handleDragonControl(EnderDragon dragon, MobControlPayload input) {
        if (dragon.getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.HOVERING) {
            dragon.getPhaseManager().setPhase(EnderDragonPhase.HOVERING);
        }
        if (input.getJump()) {
            dragon.setDeltaMovement(dragon.getDeltaMovement().add(0.0, 0.08, 0.0));
        } else if (input.getSneak()) {
            dragon.setDeltaMovement(dragon.getDeltaMovement().add(0.0, -0.05, 0.0));
        }
    }

    private static void handleWardenRoar(ServerLevel level, UUID controller, Warden warden, MobControlPayload input) {
        if (!input.getSneak()) {
            return;
        }
        if (WARDEN_ROAR_COOLDOWN.getOrDefault(controller, 0) > 0) {
            return;
        }
        WARDEN_ROAR_COOLDOWN.put(controller, 50);
        level.broadcastEntityEvent(warden, (byte) 4);
        level.playSound(null, warden.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 2.2f, 1.0f);
    }

    private static void handleVillagerAura(ServerLevel level, UUID controller, Villager villager, MobControlPayload input) {
        if (!input.getJump()) {
            return;
        }
        if (VILLAGER_AURA_COOLDOWN.getOrDefault(controller, 0) > 0) {
            return;
        }
        VILLAGER_AURA_COOLDOWN.put(controller, 80);

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(controller);
        if (player != null) {
            player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 200, 0));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 0));
        }

        level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.2f, 1.0f);

        // Small chance to call a golem ally if none nearby.
        if (level.getRandom().nextFloat() < 0.2f) {
            boolean golemNearby = !level.getEntitiesOfClass(IronGolem.class, villager.getBoundingBox().inflate(16.0)).isEmpty();
            if (!golemNearby) {
                IronGolem golem = EntityTypes.IRON_GOLEM.create(level, EntitySpawnReason.TRIGGERED);
                if (golem != null) {
                    BlockPos spawn = villager.blockPosition().offset(level.getRandom().nextInt(5) - 2, 0, level.getRandom().nextInt(5) - 2);
                    golem.snapTo(spawn, villager.getYRot(), 0.0f);
                    level.addFreshEntity(golem);
                }
            }
        }
    }

    private static void tickCooldown(Map<UUID, Integer> map, UUID playerId) {
        int v = map.getOrDefault(playerId, 0);
        if (v > 0) {
            map.put(playerId, v - 1);
        }
    }
}
