package com.qc.aeonis.llm;

import com.qc.aeonis.llm.task.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.Queue;

/**
 * The "brain" of the Aeonis assistant.
 * Manages the state machine and task execution.
 * 
 * States:
 * - IDLE: No active task, waiting for commands
 * - FOLLOWING: Following a player
 * - NAVIGATING: Walking to a destination
 * - BUILDING: Constructing a structure
 * - CHATTING: Processing an LLM chat response
 */
public class AeonisBrain {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("aeonis-brain");
    
    private final AeonisAssistant assistant;
    
    // Current task
    private AeonisTask currentTask;
    private AeonisTask.Type currentState = AeonisTask.Type.IDLE;
    
    // Task queue for sequential tasks
    private final Queue<AeonisTask> taskQueue = new LinkedList<>();
    
    // Chat task queue (separate to allow parallel processing)
    private final Queue<ChatTask> chatTaskQueue = new LinkedList<>();
    private ChatTask activeChatTask;
    
    public AeonisBrain(AeonisAssistant assistant) {
        this.assistant = assistant;
    }
    
    /**
     * Tick the brain - called every server tick
     */
    public void tick() {
        // Process chat tasks (can run in parallel with movement tasks)
        tickChatTasks();
        
        // Process main task
        tickMainTask();
    }
    
    private void tickChatTasks() {
        // Start next chat task if none active
        if (activeChatTask == null && !chatTaskQueue.isEmpty()) {
            activeChatTask = chatTaskQueue.poll();
            activeChatTask.start(assistant);
        }
        
        // Tick active chat task
        if (activeChatTask != null) {
            boolean complete = activeChatTask.tick(assistant);
            if (complete) {
                activeChatTask = null;
            }
        }
    }
    
    private void tickMainTask() {
        // If no current task, check queue
        if (currentTask == null && !taskQueue.isEmpty()) {
            setTask(taskQueue.poll());
        }
        
        // Tick current task
        if (currentTask != null) {
            boolean complete = currentTask.tick(assistant);
            if (complete) {
                currentTask = null;
                currentState = AeonisTask.Type.IDLE;
                
                // Start next task if queued
                if (!taskQueue.isEmpty()) {
                    setTask(taskQueue.poll());
                }
            }
        }
    }
    
    /**
     * Set a new task, interrupting the current one if allowed
     */
    public void setTask(AeonisTask task) {
        if (task == null) return;
        
        // Check if current task can be interrupted
        if (currentTask != null) {
            if (!currentTask.canBeInterrupted()) {
                LOGGER.info("Current task cannot be interrupted, queueing new task");
                taskQueue.offer(task);
                return;
            }
            currentTask.stop(assistant);
        }
        
        currentTask = task;
        currentState = task.getType();
        task.start(assistant);
        
        LOGGER.info("Started task: {}", task.getType());
    }
    
    /**
     * Queue a task to run after the current one completes
     */
    public void queueTask(AeonisTask task) {
        taskQueue.offer(task);
    }
    
    /**
     * Stop the current task
     */
    public void stopCurrentTask() {
        if (currentTask != null) {
            currentTask.stop(assistant);
            currentTask = null;
            currentState = AeonisTask.Type.IDLE;
        }
        taskQueue.clear();
    }
    
    /**
     * Stop all tasks including chat
     */
    public void stopAll() {
        stopCurrentTask();
        
        if (activeChatTask != null) {
            activeChatTask.stop(assistant);
            activeChatTask = null;
        }
        chatTaskQueue.clear();
    }
    
    // ================ TASK CREATION HELPERS ================
    
    /**
     * Start following a player
     */
    public void follow(ServerPlayer target) {
        setTask(new FollowTask(target));
    }
    
    /**
     * Navigate to a position
     */
    public void navigateTo(Vec3 position) {
        setTask(new NavigateTask(position));
    }
    
    /**
     * Build a structure
     */
    public void build(BuildTask.Preset preset) {
        // Build at assistant's current position, offset forward
        Vec3 pos = assistant.position();
        Vec3 lookDir = assistant.getLookAngle();
        Vec3 buildPos = pos.add(lookDir.x * 3, 0, lookDir.z * 3);
        
        setTask(new BuildTask(preset, net.minecraft.core.BlockPos.containing(buildPos)));
    }
    
    /**
     * Queue a chat message for LLM processing
     */
    public void chat(String message, ServerPlayer sender) {
        ChatTask task = new ChatTask(message, sender);
        chatTaskQueue.offer(task);
    }
    
    // ================ STATE QUERIES ================
    
    public AeonisTask.Type getCurrentState() {
        return currentState;
    }
    
    public AeonisTask getCurrentTask() {
        return currentTask;
    }
    
    public boolean isIdle() {
        return currentState == AeonisTask.Type.IDLE && currentTask == null;
    }
    
    public String getStatusMessage() {
        if (currentTask != null) {
            return currentTask.getStatusMessage();
        }
        return "Idle";
    }
    
    public int getQueuedTaskCount() {
        return taskQueue.size();
    }
    
    public int getQueuedChatCount() {
        return chatTaskQueue.size() + (activeChatTask != null ? 1 : 0);
    }
}
