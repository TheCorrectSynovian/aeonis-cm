package com.qc.aeonis.llm.task;

import com.qc.aeonis.llm.AeonisAssistant;

/**
 * Base interface for all tasks the Aeonis assistant can perform.
 */
public interface AeonisTask {
    
    /**
     * Task type enum
     */
    enum Type {
        IDLE,
        FOLLOW,
        NAVIGATE,
        BUILD,
        CHAT
    }
    
    /**
     * Get the task type
     */
    Type getType();
    
    /**
     * Start the task
     */
    void start(AeonisAssistant assistant);
    
    /**
     * Tick the task (called every server tick)
     * 
     * @param assistant The assistant executing this task
     * @return true if the task is complete, false if still running
     */
    boolean tick(AeonisAssistant assistant);
    
    /**
     * Stop/cancel the task
     */
    void stop(AeonisAssistant assistant);
    
    /**
     * Check if the task can be interrupted by another task
     */
    default boolean canBeInterrupted() {
        return true;
    }
    
    /**
     * Get a description of what the task is doing
     */
    String getStatusMessage();
}
