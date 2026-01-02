package com.qc.aeonis.llm.task;

import com.qc.aeonis.llm.AeonisAssistant;
import com.qc.aeonis.llm.safety.SafetyLimiter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Task for building predefined structures.
 * Uses the safety limiter to rate-limit block placement.
 */
public class BuildTask implements AeonisTask {
    
    /**
     * Available building presets
     */
    public enum Preset {
        SMALL_HUT("Small Hut", BuildTask::createSmallHut),
        TOWER("Tower", BuildTask::createTower),
        WALL_SEGMENT("Wall Segment", BuildTask::createWallSegment),
        PLATFORM("Platform", BuildTask::createPlatform);
        
        private final String displayName;
        private final BlueprintGenerator generator;
        
        Preset(String displayName, BlueprintGenerator generator) {
            this.displayName = displayName;
            this.generator = generator;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public List<BlockPlacement> generateBlueprint(BlockPos origin) {
            return generator.generate(origin);
        }
        
        public static Preset fromName(String name) {
            for (Preset preset : values()) {
                if (preset.name().equalsIgnoreCase(name) || preset.displayName.equalsIgnoreCase(name)) {
                    return preset;
                }
            }
            return null;
        }
    }
    
    @FunctionalInterface
    interface BlueprintGenerator {
        List<BlockPlacement> generate(BlockPos origin);
    }
    
    /**
     * A single block to be placed
     */
    public record BlockPlacement(BlockPos position, BlockState blockState) {}
    
    private final Preset preset;
    private final BlockPos origin;
    private final Queue<BlockPlacement> remainingBlocks;
    private final SafetyLimiter safetyLimiter;
    
    private int tickCooldown = 0;
    private static final int TICKS_BETWEEN_BLOCKS = 2; // Place a block every 2 ticks (10/second max)
    
    private int blocksPlaced = 0;
    private int totalBlocks;
    
    public BuildTask(Preset preset, BlockPos origin) {
        this.preset = preset;
        this.origin = origin;
        this.remainingBlocks = new LinkedList<>(preset.generateBlueprint(origin));
        this.totalBlocks = remainingBlocks.size();
        this.safetyLimiter = SafetyLimiter.getInstance();
    }
    
    @Override
    public Type getType() {
        return Type.BUILD;
    }
    
    @Override
    public void start(AeonisAssistant assistant) {
        assistant.sendChatMessage("Starting to build: " + preset.getDisplayName() + " (" + totalBlocks + " blocks)");
    }
    
    @Override
    public boolean tick(AeonisAssistant assistant) {
        // Rate limiting between ticks
        if (tickCooldown > 0) {
            tickCooldown--;
            return false;
        }
        
        if (remainingBlocks.isEmpty()) {
            assistant.sendChatMessage("Finished building " + preset.getDisplayName() + "! Placed " + blocksPlaced + " blocks.");
            return true; // Task complete
        }
        
        BlockPlacement placement = remainingBlocks.peek();
        
        // Check safety limits
        SafetyLimiter.SafetyCheck check = safetyLimiter.canEditBlock(
            placement.position(), 
            SafetyLimiter.EditType.PLACE, 
            assistant.level()
        );
        
        if (!check.isAllowed()) {
            assistant.sendChatMessage("Can't place block: " + check.reason());
            // Wait and retry
            tickCooldown = 20; // Wait 1 second
            return false;
        }
        
        // Place the block
        boolean success = assistant.placeBlock(placement.position(), placement.blockState());
        
        if (success) {
            remainingBlocks.poll();
            blocksPlaced++;
            
            // Record the edit
            safetyLimiter.recordEdit(
                placement.position(),
                placement.blockState().getBlock().getName().getString(),
                SafetyLimiter.EditType.PLACE,
                "BuildTask: " + preset.getDisplayName()
            );
            
            // Progress update every 10 blocks
            if (blocksPlaced % 10 == 0) {
                int remaining = remainingBlocks.size();
                assistant.sendChatMessage("Building progress: " + blocksPlaced + "/" + totalBlocks + " (" + remaining + " remaining)");
            }
        }
        
        tickCooldown = TICKS_BETWEEN_BLOCKS;
        return false;
    }
    
    @Override
    public void stop(AeonisAssistant assistant) {
        int remaining = remainingBlocks.size();
        assistant.sendChatMessage("Build cancelled. Placed " + blocksPlaced + "/" + totalBlocks + " blocks. (" + remaining + " remaining)");
    }
    
    @Override
    public boolean canBeInterrupted() {
        return true;
    }
    
    @Override
    public String getStatusMessage() {
        return "Building " + preset.getDisplayName() + " (" + blocksPlaced + "/" + totalBlocks + ")";
    }
    
    // ================ BLUEPRINT GENERATORS ================
    
    /**
     * Small 5x5x4 hut with door opening
     */
    private static List<BlockPlacement> createSmallHut(BlockPos origin) {
        List<BlockPlacement> blocks = new ArrayList<>();
        BlockState oakPlanks = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState oakLog = Blocks.OAK_LOG.defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState();
        
        // Floor (5x5)
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                blocks.add(new BlockPlacement(origin.offset(x, 0, z), oakPlanks));
            }
        }
        
        // Walls (3 high)
        for (int y = 1; y <= 3; y++) {
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 5; z++) {
                    // Only edges
                    if (x == 0 || x == 4 || z == 0 || z == 4) {
                        // Door opening on front
                        if (z == 0 && x == 2 && y <= 2) continue;
                        // Windows on sides
                        if (y == 2 && ((x == 2 && (z == 0 || z == 4)) || (z == 2 && (x == 0 || x == 4)))) {
                            blocks.add(new BlockPlacement(origin.offset(x, y, z), glass));
                        }
                        // Corners are logs
                        else if ((x == 0 || x == 4) && (z == 0 || z == 4)) {
                            blocks.add(new BlockPlacement(origin.offset(x, y, z), oakLog));
                        } else {
                            blocks.add(new BlockPlacement(origin.offset(x, y, z), oakPlanks));
                        }
                    }
                }
            }
        }
        
        // Roof (flat for simplicity)
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                blocks.add(new BlockPlacement(origin.offset(x, 4, z), oakPlanks));
            }
        }
        
        return blocks;
    }
    
    /**
     * Simple watchtower (3x3 base, 8 high)
     */
    private static List<BlockPlacement> createTower(BlockPos origin) {
        List<BlockPlacement> blocks = new ArrayList<>();
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        BlockState stoneBrick = Blocks.STONE_BRICKS.defaultBlockState();
        
        // Base and walls
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    // Hollow inside except floor
                    if (y == 0 || x == 0 || x == 2 || z == 0 || z == 2) {
                        // Door opening
                        if (z == 0 && x == 1 && y > 0 && y <= 2) continue;
                        
                        blocks.add(new BlockPlacement(origin.offset(x, y, z), y % 2 == 0 ? cobble : stoneBrick));
                    }
                }
            }
        }
        
        // Top platform (5x5)
        for (int x = -1; x < 4; x++) {
            for (int z = -1; z < 4; z++) {
                blocks.add(new BlockPlacement(origin.offset(x, 8, z), stoneBrick));
            }
        }
        
        // Battlements
        for (int x = -1; x < 4; x++) {
            for (int z = -1; z < 4; z++) {
                if (x == -1 || x == 3 || z == -1 || z == 3) {
                    if ((x + z) % 2 == 0) {
                        blocks.add(new BlockPlacement(origin.offset(x, 9, z), cobble));
                    }
                }
            }
        }
        
        return blocks;
    }
    
    /**
     * Wall segment (10 long, 4 high)
     */
    private static List<BlockPlacement> createWallSegment(BlockPos origin) {
        List<BlockPlacement> blocks = new ArrayList<>();
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        BlockState stoneBrick = Blocks.STONE_BRICKS.defaultBlockState();
        
        for (int x = 0; x < 10; x++) {
            for (int y = 0; y < 4; y++) {
                blocks.add(new BlockPlacement(origin.offset(x, y, 0), y % 2 == 0 ? cobble : stoneBrick));
            }
            // Battlements on top
            if (x % 2 == 0) {
                blocks.add(new BlockPlacement(origin.offset(x, 4, 0), cobble));
            }
        }
        
        return blocks;
    }
    
    /**
     * Simple platform (7x7)
     */
    private static List<BlockPlacement> createPlatform(BlockPos origin) {
        List<BlockPlacement> blocks = new ArrayList<>();
        BlockState oakPlanks = Blocks.OAK_PLANKS.defaultBlockState();
        
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                blocks.add(new BlockPlacement(origin.offset(x, 0, z), oakPlanks));
            }
        }
        
        return blocks;
    }
    
    /**
     * Get all available preset names
     */
    public static List<String> getPresetNames() {
        return Arrays.stream(Preset.values()).map(Preset::getDisplayName).toList();
    }
}
