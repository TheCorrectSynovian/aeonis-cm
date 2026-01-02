package com.qc.aeonis.llm.task;

import com.qc.aeonis.llm.AeonisAssistant;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Task for navigating to a specific position.
 */
public class NavigateTask implements AeonisTask {
    
    private final Vec3 targetPosition;
    private final double arrivalDistance;
    
    private boolean started = false;
    private int stuckTicks = 0;
    private Vec3 lastPosition;
    private static final int STUCK_THRESHOLD = 60; // 3 seconds without movement
    
    public NavigateTask(Vec3 targetPosition) {
        this(targetPosition, 1.5);
    }
    
    public NavigateTask(Vec3 targetPosition, double arrivalDistance) {
        this.targetPosition = targetPosition;
        this.arrivalDistance = arrivalDistance;
    }
    
    public NavigateTask(BlockPos blockPos) {
        this(Vec3.atCenterOf(blockPos));
    }
    
    @Override
    public Type getType() {
        return Type.NAVIGATE;
    }
    
    @Override
    public void start(AeonisAssistant assistant) {
        started = true;
        lastPosition = assistant.position();
        assistant.navigateTo(targetPosition);
        
        BlockPos blockPos = BlockPos.containing(targetPosition);
        assistant.sendChatMessage("Walking to " + blockPos.getX() + ", " + blockPos.getY() + ", " + blockPos.getZ());
    }
    
    @Override
    public boolean tick(AeonisAssistant assistant) {
        if (!started) {
            start(assistant);
        }
        
        Vec3 currentPos = assistant.position();
        double distance = currentPos.distanceTo(targetPosition);
        
        // Check if arrived
        if (distance <= arrivalDistance) {
            BlockPos blockPos = BlockPos.containing(targetPosition);
            assistant.sendChatMessage("Arrived at destination! (" + blockPos.getX() + ", " + blockPos.getY() + ", " + blockPos.getZ() + ")");
            return true; // Task complete
        }
        
        // Check if stuck
        if (lastPosition != null) {
            double movedDistance = currentPos.distanceTo(lastPosition);
            if (movedDistance < 0.1) {
                stuckTicks++;
                if (stuckTicks > STUCK_THRESHOLD) {
                    assistant.sendChatMessage("I seem to be stuck! Can't reach the destination.");
                    return true; // Give up
                }
            } else {
                stuckTicks = 0;
            }
        }
        lastPosition = currentPos;
        
        // Look towards target
        assistant.lookAt(targetPosition);
        
        return false; // Still navigating
    }
    
    @Override
    public void stop(AeonisAssistant assistant) {
        assistant.stopNavigation();
        assistant.sendChatMessage("Navigation cancelled.");
    }
    
    @Override
    public String getStatusMessage() {
        BlockPos blockPos = BlockPos.containing(targetPosition);
        return "Navigating to " + blockPos.getX() + ", " + blockPos.getY() + ", " + blockPos.getZ();
    }
    
    public Vec3 getTargetPosition() {
        return targetPosition;
    }
}
