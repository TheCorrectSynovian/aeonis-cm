package com.qc.aeonis.llm.safety;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Safety limiter to prevent griefing by the AI assistant.
 * 
 * Features:
 * - Rate limiting: Max blocks placed per minute
 * - Radius limiting: Max distance from spawn/owner the bot can edit
 * - Grief protection: Prevents breaking certain protected blocks
 * - Logging: All edits are logged for audit
 */
public class SafetyLimiter {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("aeonis-llm-safety");
    
    // Default limits
    private int maxBlocksPerMinute = 30;
    private int maxEditRadius = 50;
    private boolean griefProtectionEnabled = true;
    
    // Rate limiting state
    private final Deque<Long> blockPlaceTimestamps = new LinkedList<>();
    private final Object rateLimitLock = new Object();
    
    // Edit history for audit
    private final List<BlockEdit> editHistory = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_HISTORY_SIZE = 1000;
    
    // Protected areas (spawn, player claims, etc.)
    private final Set<BlockPos> protectedPositions = ConcurrentHashMap.newKeySet();
    
    // Owner position tracking for radius check
    private BlockPos ownerPosition;
    private BlockPos botSpawnPosition;
    
    public SafetyLimiter() {}
    
    /**
     * Record of a block edit for audit purposes
     */
    public record BlockEdit(
        long timestamp,
        BlockPos position,
        String blockType,
        EditType editType,
        String reason
    ) {}
    
    public enum EditType {
        PLACE,
        BREAK,
        INTERACT
    }
    
    /**
     * Check result with reason
     */
    public record SafetyCheck(boolean isAllowed, String reason) {
        public static SafetyCheck allowed() {
            return new SafetyCheck(true, null);
        }
        
        public static SafetyCheck denied(String reason) {
            return new SafetyCheck(false, reason);
        }
    }
    
    // ================ CONFIGURATION ================
    
    public void setMaxBlocksPerMinute(int max) {
        this.maxBlocksPerMinute = Math.max(1, max);
    }
    
    public void setMaxEditRadius(int radius) {
        this.maxEditRadius = Math.max(1, radius);
    }
    
    public void setGriefProtectionEnabled(boolean enabled) {
        this.griefProtectionEnabled = enabled;
    }
    
    public void setOwnerPosition(BlockPos pos) {
        this.ownerPosition = pos;
    }
    
    public void setBotSpawnPosition(BlockPos pos) {
        this.botSpawnPosition = pos;
    }
    
    // ================ SAFETY CHECKS ================
    
    /**
     * Check if a block edit is allowed
     */
    public SafetyCheck canEditBlock(BlockPos pos, EditType editType, Level level) {
        // Check rate limit
        if (!checkRateLimit()) {
            return SafetyCheck.denied("Rate limit exceeded (" + maxBlocksPerMinute + " blocks/minute)");
        }
        
        // Check radius limit
        if (!checkRadius(pos)) {
            return SafetyCheck.denied("Position outside allowed radius (" + maxEditRadius + " blocks)");
        }
        
        // Check protected positions
        if (griefProtectionEnabled && isProtected(pos, level)) {
            return SafetyCheck.denied("Position is protected");
        }
        
        return SafetyCheck.allowed();
    }
    
    /**
     * Record that a block was edited (call after successful edit)
     */
    public void recordEdit(BlockPos pos, String blockType, EditType editType, String reason) {
        // Update rate limit tracker
        synchronized (rateLimitLock) {
            blockPlaceTimestamps.addLast(System.currentTimeMillis());
        }
        
        // Add to history
        BlockEdit edit = new BlockEdit(
            System.currentTimeMillis(),
            pos,
            blockType,
            editType,
            reason
        );
        
        editHistory.add(edit);
        
        // Trim history if needed
        while (editHistory.size() > MAX_HISTORY_SIZE) {
            editHistory.remove(0);
        }
        
        LOGGER.debug("Block edit recorded: {} {} at {} - {}", editType, blockType, pos, reason);
    }
    
    /**
     * Check rate limit
     */
    private boolean checkRateLimit() {
        long oneMinuteAgo = System.currentTimeMillis() - 60_000;
        
        synchronized (rateLimitLock) {
            // Remove old timestamps
            while (!blockPlaceTimestamps.isEmpty() && blockPlaceTimestamps.peekFirst() < oneMinuteAgo) {
                blockPlaceTimestamps.pollFirst();
            }
            
            // Check if under limit
            return blockPlaceTimestamps.size() < maxBlocksPerMinute;
        }
    }
    
    /**
     * Check if position is within allowed radius
     */
    private boolean checkRadius(BlockPos pos) {
        // If no reference position, allow all
        if (ownerPosition == null && botSpawnPosition == null) {
            return true;
        }
        
        // Check distance from owner
        if (ownerPosition != null) {
            double distFromOwner = Math.sqrt(pos.distSqr(ownerPosition));
            if (distFromOwner <= maxEditRadius) {
                return true;
            }
        }
        
        // Check distance from spawn
        if (botSpawnPosition != null) {
            double distFromSpawn = Math.sqrt(pos.distSqr(botSpawnPosition));
            if (distFromSpawn <= maxEditRadius) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if a position is protected
     */
    private boolean isProtected(BlockPos pos, Level level) {
        // Check explicit protected positions
        if (protectedPositions.contains(pos)) {
            return true;
        }
        
        // Note: World spawn protection is disabled for now
        // The radius-based limiting from owner/bot position is sufficient
        
        return false;
    }
    
    /**
     * Add a protected position
     */
    public void addProtectedPosition(BlockPos pos) {
        protectedPositions.add(pos);
    }
    
    /**
     * Remove a protected position
     */
    public void removeProtectedPosition(BlockPos pos) {
        protectedPositions.remove(pos);
    }
    
    /**
     * Get blocks remaining in rate limit
     */
    public int getRemainingBlocksInLimit() {
        long oneMinuteAgo = System.currentTimeMillis() - 60_000;
        
        synchronized (rateLimitLock) {
            // Remove old timestamps
            while (!blockPlaceTimestamps.isEmpty() && blockPlaceTimestamps.peekFirst() < oneMinuteAgo) {
                blockPlaceTimestamps.pollFirst();
            }
            
            return Math.max(0, maxBlocksPerMinute - blockPlaceTimestamps.size());
        }
    }
    
    /**
     * Get recent edit history
     */
    public List<BlockEdit> getRecentEdits(int count) {
        int start = Math.max(0, editHistory.size() - count);
        return new ArrayList<>(editHistory.subList(start, editHistory.size()));
    }
    
    /**
     * Reset rate limiter (e.g., after bot restart)
     */
    public void reset() {
        synchronized (rateLimitLock) {
            blockPlaceTimestamps.clear();
        }
        editHistory.clear();
        LOGGER.info("Safety limiter reset");
    }
    
    // ================ SINGLETON ================
    
    private static SafetyLimiter instance;
    
    public static SafetyLimiter getInstance() {
        if (instance == null) {
            instance = new SafetyLimiter();
        }
        return instance;
    }
    
    public static void init(int maxBlocks, int maxRadius, boolean griefProtection) {
        getInstance().setMaxBlocksPerMinute(maxBlocks);
        getInstance().setMaxEditRadius(maxRadius);
        getInstance().setGriefProtectionEnabled(griefProtection);
    }
}
