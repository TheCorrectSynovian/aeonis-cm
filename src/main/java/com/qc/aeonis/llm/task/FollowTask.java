package com.qc.aeonis.llm.task;

import com.qc.aeonis.llm.AeonisAssistant;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Task for following a player.
 * The assistant will pathfind to stay near the target player.
 */
public class FollowTask implements AeonisTask {
    
    private final ServerPlayer target;
    private final double followDistance;
    private final double minDistance;
    
    private int repathCooldown = 0;
    private static final int REPATH_INTERVAL = 20; // Re-path every second
    
    public FollowTask(ServerPlayer target) {
        this(target, 3.0, 2.0);
    }
    
    public FollowTask(ServerPlayer target, double followDistance, double minDistance) {
        this.target = target;
        this.followDistance = followDistance;
        this.minDistance = minDistance;
    }
    
    @Override
    public Type getType() {
        return Type.FOLLOW;
    }
    
    @Override
    public void start(AeonisAssistant assistant) {
        assistant.sendChatMessage("Following " + target.getName().getString() + "!");
    }
    
    @Override
    public boolean tick(AeonisAssistant assistant) {
        // Check if target is still valid
        if (target == null || target.isRemoved() || !target.isAlive()) {
            assistant.sendChatMessage("Lost track of the target!");
            return true; // Task complete (target gone)
        }
        
        // Check if in same dimension
        if (!target.level().equals(assistant.level())) {
            assistant.sendChatMessage("Target is in a different dimension!");
            return true;
        }
        
        Vec3 targetPos = target.position();
        Vec3 myPos = assistant.position();
        double distance = myPos.distanceTo(targetPos);
        
        // If too close, just look at the target
        if (distance < minDistance) {
            assistant.lookAt(target);
            return false; // Keep following
        }
        
        // Re-path periodically
        repathCooldown--;
        if (repathCooldown <= 0) {
            repathCooldown = REPATH_INTERVAL;
            
            // Calculate position to move to (towards target but stop at follow distance)
            Vec3 direction = targetPos.subtract(myPos).normalize();
            Vec3 targetStop = targetPos.subtract(direction.scale(followDistance));
            
            assistant.navigateTo(targetStop);
        }
        
        // Look at target while moving
        assistant.lookAt(target);
        
        return false; // Never completes on its own
    }
    
    @Override
    public void stop(AeonisAssistant assistant) {
        assistant.stopNavigation();
        assistant.sendChatMessage("Stopped following " + target.getName().getString());
    }
    
    @Override
    public String getStatusMessage() {
        if (target != null) {
            return "Following " + target.getName().getString();
        }
        return "Following (no target)";
    }
    
    public ServerPlayer getTarget() {
        return target;
    }
}
