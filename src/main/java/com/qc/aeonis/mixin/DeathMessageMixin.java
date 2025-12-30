package com.qc.aeonis.mixin;

import com.qc.aeonis.network.AeonisNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to modify death messages when a player controlling a mob kills another entity.
 * The death message will show "Killed by [Mob Name]" instead of the player's name.
 */
@Mixin(CombatTracker.class)
public abstract class DeathMessageMixin {
    
    @Shadow @Final
    private LivingEntity mob;
    
    /**
     * Intercept death message generation to show controlled mob as killer
     */
    @Inject(method = "getDeathMessage", at = @At("HEAD"), cancellable = true)
    private void aeonis$modifyDeathMessage(CallbackInfoReturnable<Component> cir) {
        LivingEntity victim = this.mob;
        if (victim == null) return;
        
        // Get the last damage source
        DamageSource lastDamage = victim.getLastDamageSource();
        if (lastDamage == null) return;
        
        // Check if the attacker is a player controlling a mob
        Entity attacker = lastDamage.getEntity();
        if (!(attacker instanceof Player player)) return;
        
        // Check if the player is controlling a mob
        if (!AeonisNetworking.INSTANCE.isPlayerTransformed(player.getUUID())) return;
        
        Integer controlledId = AeonisNetworking.INSTANCE.getControlledEntityId(player.getUUID());
        if (controlledId == null) return;
        
        Entity controlledEntity = player.level().getEntity(controlledId);
        if (!(controlledEntity instanceof Mob controlledMob)) return;
        
        // Create custom death message showing the mob as the killer
        String victimName = victim.getName().getString();
        String mobName = controlledMob.getName().getString();
        
        // Format: "[Victim] was slain by [Mob]"
        Component deathMessage = Component.literal(victimName + " was slain by " + mobName);
        cir.setReturnValue(deathMessage);
    }
}
