package com.qc.aeonis.llm;

import com.mojang.authlib.GameProfile;
import com.qc.aeonis.llm.safety.SafetyLimiter;
import com.qc.aeonis.llm.script.AeonisScriptManager;
import com.qc.aeonis.llm.util.FakePlayerPacketUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The Aeonis AI Assistant - a fake player that can be controlled by LLM.
 * 
 * This extends ServerPlayer to appear as a real player in the game,
 * but is controlled by the AeonisBrain state machine.
 * 
 * Limitations in Fabric (compared to Paper/Bukkit):
 * - No native FakePlayer API, so we extend ServerPlayer
 * - Must manually handle network packets for visibility
 * - Pathfinding requires custom implementation or navigation from mobs
 */
public class AeonisAssistant extends ServerPlayer {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("aeonis-assistant");
    
    public static final String AEONIS_NAME = "Aeonis";
    public static final UUID AEONIS_UUID = UUID.nameUUIDFromBytes(("OfflinePlayer:" + AEONIS_NAME).getBytes());
    
    private final MinecraftServer minecraftServer; // Store server reference
    private final AeonisBrain aeonisBrain;
    private final SafetyLimiter safetyLimiter;
    
    // Navigation state
    private Vec3 targetPosition;
    private Path currentPath;
    private int pathfindingCooldown = 0;
    
    // Chat response queue (for thread-safe LLM responses)
    private final Queue<String> chatResponseQueue = new ConcurrentLinkedQueue<>();
    
    // Owner (who spawned the assistant)
    private UUID ownerUuid;
    
    // Static instance tracking
    private static final Map<ServerLevel, AeonisAssistant> instances = new HashMap<>();
    
    /**
     * Create a new Aeonis assistant
     */
    public AeonisAssistant(MinecraftServer server, ServerLevel level) {
        super(server, level, createGameProfile(), createClientInfo());
        
        this.minecraftServer = server;
        this.aeonisBrain = new AeonisBrain(this);
        this.safetyLimiter = SafetyLimiter.getInstance();
        
        // Set initial position
        this.setGameMode(GameType.SURVIVAL);
        
        LOGGER.info("Aeonis assistant created in {}", level.dimension().identifier());
    }
    
    private static GameProfile createGameProfile() {
        return new GameProfile(AEONIS_UUID, AEONIS_NAME);
    }
    
    private static ClientInformation createClientInfo() {
        // In 1.21, ClientInformation has a static createDefault() method
        return ClientInformation.createDefault();
    }
    
    // ================ LIFECYCLE ================
    
    // Track if this assistant is still active (not despawned)
    private boolean active = true;
    
    /**
     * Spawn the assistant at a player's location
     */
    public static AeonisAssistant spawn(ServerPlayer owner) {
        ServerLevel level = (ServerLevel) owner.level();
        
        // Check if one already exists in this level
        if (instances.containsKey(level)) {
            AeonisAssistant existing = instances.get(level);
            if (existing.isActive()) {
                // Debug level - not worth spamming info for this
                LOGGER.debug("Aeonis already exists in this dimension, returning existing instance");
                return existing;
            } else {
                instances.remove(level);
            }
        }
        
        // Create new instance
        AeonisAssistant assistant = new AeonisAssistant(level.getServer(), level);
        assistant.ownerUuid = owner.getUUID();
        
        // Position at owner
        assistant.setPos(owner.getX(), owner.getY(), owner.getZ());
        assistant.setYRot(owner.getYRot());
        assistant.setXRot(owner.getXRot());
        
        // Update safety limiter
        assistant.safetyLimiter.setBotSpawnPosition(assistant.blockPosition());
        assistant.safetyLimiter.setOwnerPosition(owner.blockPosition());
        
        // Don't add as entity - ServerPlayer shouldn't be added via addFreshEntity
        // Instead, just track internally and tick manually
        instances.put(level, assistant);
        
        // Send player info to all players so Aeonis appears in tab list
        broadcastPlayerInfo(assistant, true);
        
        assistant.sendChatMessage("Hello! I'm Aeonis, your AI assistant. How can I help?");
        
        LOGGER.info("Aeonis spawned at {} by {}", assistant.position(), owner.getName().getString());
        
        return assistant;
    }
    
    /**
     * Check if this assistant is still active
     */
    public boolean isActive() {
        return active;
    }
    
    /**
     * Despawn the assistant
     */
    public void despawn() {
        sendChatMessage("Goodbye!");
        
        // Mark as inactive
        this.active = false;
        
        // Remove from player list
        broadcastPlayerInfo(this, false);
        
        // Not in world as entity, so just clean up tracking
        ServerLevel serverLevel = (ServerLevel) this.level();
        instances.remove(serverLevel);
        
        LOGGER.info("Aeonis despawned");
    }
    
    /**
     * Get the instance for a level (if exists)
     */
    public static AeonisAssistant getInstance(ServerLevel level) {
        return instances.get(level);
    }
    
    /**
     * Check if an assistant exists in any level
     */
    public static boolean exists() {
        return !instances.isEmpty();
    }
    
    /**
     * Despawn all instances
     */
    public static void despawnAll() {
        for (AeonisAssistant assistant : new ArrayList<>(instances.values())) {
            assistant.despawn();
        }
        instances.clear();
    }
    
    // ================ TICK ================
    
    private int positionBroadcastCooldown = 0;
    
    @Override
    public void tick() {
        // DON'T call super.tick() - ServerPlayer.tick() tries to use connection which is null
        // Instead, do minimal entity ticking ourselves
        
        // Basic entity tick (movement, physics)
        this.baseTick();
        
        // Update owner position for safety radius
        if (ownerUuid != null) {
            ServerPlayer owner = this.minecraftServer.getPlayerList().getPlayer(ownerUuid);
            if (owner != null && owner.level() == this.level()) {
                safetyLimiter.setOwnerPosition(owner.blockPosition());
            }
        }
        
        // Tick pathfinding
        tickNavigation();
        
        // Tick brain
        aeonisBrain.tick();
        
        // Tick automation scripts
        AeonisScriptManager scriptManager = AeonisScriptManager.getInstance();
        if (scriptManager != null) {
            scriptManager.tickAutomation(this);
        }
        
        // Broadcast position updates every few ticks
        positionBroadcastCooldown--;
        if (positionBroadcastCooldown <= 0) {
            positionBroadcastCooldown = 2; // Every 2 ticks (10 times per second)
            broadcastPositionUpdate();
        }
    }
    
    // ================ NAVIGATION ================
    
    /**
     * Navigate to a position using simple pathfinding
     */
    public void navigateTo(Vec3 target) {
        this.targetPosition = target;
        this.pathfindingCooldown = 0;
        recalculatePath();
    }
    
    /**
     * Stop navigation
     */
    public void stopNavigation() {
        this.targetPosition = null;
        this.currentPath = null;
    }
    
    private void tickNavigation() {
        if (targetPosition == null) return;
        
        // Recalculate path periodically
        pathfindingCooldown--;
        if (pathfindingCooldown <= 0) {
            pathfindingCooldown = 20; // Every second
            recalculatePath();
        }
        
        // Simple movement towards target
        Vec3 currentPos = position();
        Vec3 direction = targetPosition.subtract(currentPos);
        double distance = direction.horizontalDistance();
        
        if (distance < 0.5) {
            // Arrived
            return;
        }
        
        // Normalize and move
        Vec3 horizontalDir = new Vec3(direction.x, 0, direction.z).normalize();
        
        // Calculate movement
        double speed = 0.23; // blocks per tick (slightly faster)
        double moveX = horizontalDir.x * speed;
        double moveZ = horizontalDir.z * speed;
        
        // Check blocks for jumping/obstacles
        Vec3 newPos = new Vec3(currentPos.x + moveX, currentPos.y, currentPos.z + moveZ);
        BlockPos feetPos = BlockPos.containing(newPos.x, currentPos.y, newPos.z);
        BlockPos kneePos = BlockPos.containing(newPos.x, currentPos.y + 0.5, newPos.z);
        BlockPos headPos = BlockPos.containing(newPos.x, currentPos.y + 1.8, newPos.z);
        BlockPos aboveFeet = feetPos.above();
        BlockPos twoAboveFeet = feetPos.above(2);
        
        BlockState feetState = level().getBlockState(feetPos);
        BlockState kneeState = level().getBlockState(kneePos);
        BlockState aboveFeetState = level().getBlockState(aboveFeet);
        BlockState twoAboveState = level().getBlockState(twoAboveFeet);
        
        boolean onGround = this.onGround();
        double yVel = this.getDeltaMovement().y;
        
        // Check if we need to jump
        boolean shouldJump = false;
        
        // Jump if there's a block at feet/knee level but space above
        if (!feetState.isAir() || !kneeState.isAir()) {
            // There's an obstacle - check if we can jump over it
            if (aboveFeetState.isAir() && twoAboveState.isAir()) {
                shouldJump = true;
            }
        }
        
        // Also check if target is above us
        if (targetPosition.y > currentPos.y + 0.5 && onGround) {
            shouldJump = true;
        }
        
        // Check for 1-block step up
        BlockPos checkAhead = BlockPos.containing(currentPos.x + moveX * 2, currentPos.y, currentPos.z + moveZ * 2);
        BlockState aheadState = level().getBlockState(checkAhead);
        BlockState aboveAhead = level().getBlockState(checkAhead.above());
        BlockState twoAboveAhead = level().getBlockState(checkAhead.above(2));
        
        if (!aheadState.isAir() && aboveAhead.isAir() && twoAboveAhead.isAir() && onGround) {
            shouldJump = true;
        }
        
        // Apply movement
        if (shouldJump && onGround) {
            // Jump!
            this.setDeltaMovement(moveX, 0.42, moveZ); // 0.42 is vanilla jump velocity
        } else {
            // Normal movement with gravity
            if (onGround) {
                this.setDeltaMovement(moveX, yVel, moveZ);
            } else {
                // In air - apply gravity and keep horizontal momentum
                this.setDeltaMovement(moveX * 0.91, yVel - 0.08, moveZ * 0.91);
            }
        }
        
        // Apply movement
        this.move(net.minecraft.world.entity.MoverType.SELF, this.getDeltaMovement());
        
        // Face movement direction
        float yaw = (float) (Math.atan2(-moveX, moveZ) * (180.0 / Math.PI));
        this.setYRot(yaw);
        this.setYHeadRot(yaw);
    }
    
    private void recalculatePath() {
        // Simple A* pathfinding could be added here
        // For now, we use direct movement
    }
    
    // ================ ACTIONS ================
    
    /**
     * Look at an entity
     */
    public void lookAt(Entity target) {
        lookAt(target.position().add(0, target.getEyeHeight(), 0));
    }
    
    /**
     * Look at a position
     */
    public void lookAt(Vec3 target) {
        Vec3 myPos = this.position().add(0, this.getEyeHeight(), 0);
        Vec3 direction = target.subtract(myPos);
        
        double horizontalDist = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        float yaw = (float) (Math.atan2(-direction.x, direction.z) * (180.0 / Math.PI));
        float pitch = (float) (Math.atan2(-direction.y, horizontalDist) * (180.0 / Math.PI));
        
        this.setYRot(yaw);
        this.setXRot(pitch);
        this.setYHeadRot(yaw);
    }
    
    /**
     * Place a block at a position
     */
    public boolean placeBlock(BlockPos pos, BlockState state) {
        // Safety check
        SafetyLimiter.SafetyCheck check = safetyLimiter.canEditBlock(pos, SafetyLimiter.EditType.PLACE, level());
        if (!check.isAllowed()) {
            LOGGER.warn("Block placement denied: {}", check.reason());
            return false;
        }
        
        // Check if position is valid for placement
        if (!level().getBlockState(pos).canBeReplaced()) {
            return false;
        }
        
        // Place the block
        boolean success = level().setBlockAndUpdate(pos, state);
        
        if (success) {
            // Play sound - in 1.21, getSoundType takes no parameters
            level().playSound(null, pos, state.getSoundType().getPlaceSound(),
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        
        return success;
    }
    
    /**
     * Send a chat message as Aeonis
     */
    public void sendChatMessage(String message) {
        // Broadcast to all players in the level
        Component chatMessage = Component.literal("<" + AEONIS_NAME + "> " + message);
        
        for (ServerPlayer player : this.minecraftServer.getPlayerList().getPlayers()) {
            if (player.level() == this.level()) {
                player.sendSystemMessage(chatMessage);
            }
        }
        
        LOGGER.info("Aeonis: {}", message);
    }
    
    /**
     * Queue a chat response (called from async LLM thread)
     */
    public void queueChatResponse(String response) {
        chatResponseQueue.offer(response);
    }
    
    /**
     * Process queued chat responses (called from main thread)
     * Also parses and executes any commands found in the response.
     */
    public void processQueuedChatResponses() {
        String response;
        while ((response = chatResponseQueue.poll()) != null) {
            // Parse and execute any embedded commands first
            response = parseAndExecuteCommands(response);
            
            // Split long responses into multiple messages
            if (response.length() > 200) {
                // Split at sentence boundaries or spaces
                List<String> parts = splitMessage(response, 200);
                for (String part : parts) {
                    if (!part.trim().isEmpty()) {
                        sendChatMessage(part);
                    }
                }
            } else if (!response.trim().isEmpty()) {
                sendChatMessage(response);
            }
        }
    }
    
    /**
     * Parse AI response for commands and execute them.
     * Commands can be in these formats:
     * - [CMD: /command here]
     * - [EXECUTE: /command here]
     * - [RUN: /command here]
     * - [SCRIPT: name]...[/SCRIPT]
     * - [RUNSCRIPT: name]
     * - [NEWCMD: name | description]...[/NEWCMD]
     * - [RUNCMD: name]
     * - [AUTOMATION: name | seconds]...[/AUTOMATION]
     * - [STOPAUTO: name]
     * - ```command\n/command here\n```
     * 
     * Returns the response with command blocks removed.
     */
    private String parseAndExecuteCommands(String response) {
        StringBuilder cleanResponse = new StringBuilder();
        String remaining = response;
        
        // Pattern 1: [CMD: /command] or [EXECUTE: /command] or [RUN: /command]
        java.util.regex.Pattern cmdPattern = java.util.regex.Pattern.compile(
            "\\[(CMD|EXECUTE|RUN):\\s*(/[^\\]]+)\\]",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        
        java.util.regex.Matcher matcher = cmdPattern.matcher(remaining);
        int lastEnd = 0;
        
        while (matcher.find()) {
            cleanResponse.append(remaining, lastEnd, matcher.start());
            lastEnd = matcher.end();
            String command = matcher.group(2).trim();
            executeCommand(command);
        }
        cleanResponse.append(remaining.substring(lastEnd));
        remaining = cleanResponse.toString();
        
        // Pattern: [SCRIPT: name]...[/SCRIPT]
        cleanResponse = new StringBuilder();
        java.util.regex.Pattern scriptPattern = java.util.regex.Pattern.compile(
            "\\[SCRIPT:\\s*([^\\]]+)\\]([\\s\\S]*?)\\[/SCRIPT\\]",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        matcher = scriptPattern.matcher(remaining);
        lastEnd = 0;
        while (matcher.find()) {
            cleanResponse.append(remaining, lastEnd, matcher.start());
            lastEnd = matcher.end();
            String scriptName = matcher.group(1).trim();
            String scriptContent = matcher.group(2).trim();
            createScript(scriptName, scriptContent);
        }
        cleanResponse.append(remaining.substring(lastEnd));
        remaining = cleanResponse.toString();
        
        // Pattern: [RUNSCRIPT: name]
        cleanResponse = new StringBuilder();
        java.util.regex.Pattern runScriptPattern = java.util.regex.Pattern.compile(
            "\\[RUNSCRIPT:\\s*([^\\]]+)\\]",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        matcher = runScriptPattern.matcher(remaining);
        lastEnd = 0;
        while (matcher.find()) {
            cleanResponse.append(remaining, lastEnd, matcher.start());
            lastEnd = matcher.end();
            String scriptName = matcher.group(1).trim();
            runSavedScript(scriptName);
        }
        cleanResponse.append(remaining.substring(lastEnd));
        remaining = cleanResponse.toString();
        
        // Pattern: [NEWCMD: name | description]...[/NEWCMD]
        cleanResponse = new StringBuilder();
        java.util.regex.Pattern newCmdPattern = java.util.regex.Pattern.compile(
            "\\[NEWCMD:\\s*([^|\\]]+)\\|([^\\]]+)\\]([\\s\\S]*?)\\[/NEWCMD\\]",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        matcher = newCmdPattern.matcher(remaining);
        lastEnd = 0;
        while (matcher.find()) {
            cleanResponse.append(remaining, lastEnd, matcher.start());
            lastEnd = matcher.end();
            String cmdName = matcher.group(1).trim();
            String description = matcher.group(2).trim();
            String cmdContent = matcher.group(3).trim();
            
            // Parse commands from content
            List<String> commands = new ArrayList<>();
            for (String line : cmdContent.split("\\n")) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    if (line.startsWith("/")) line = line.substring(1);
                    commands.add(line);
                }
            }
            createCustomCommand(cmdName, description, commands);
        }
        cleanResponse.append(remaining.substring(lastEnd));
        remaining = cleanResponse.toString();
        
        // Pattern: [RUNCMD: name]
        cleanResponse = new StringBuilder();
        java.util.regex.Pattern runCmdPattern = java.util.regex.Pattern.compile(
            "\\[RUNCMD:\\s*([^\\]]+)\\]",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        matcher = runCmdPattern.matcher(remaining);
        lastEnd = 0;
        while (matcher.find()) {
            cleanResponse.append(remaining, lastEnd, matcher.start());
            lastEnd = matcher.end();
            String cmdName = matcher.group(1).trim();
            runCustomCommand(cmdName);
        }
        cleanResponse.append(remaining.substring(lastEnd));
        remaining = cleanResponse.toString();
        
        // Pattern: [AUTOMATION: name | seconds]...[/AUTOMATION]
        cleanResponse = new StringBuilder();
        java.util.regex.Pattern autoPattern = java.util.regex.Pattern.compile(
            "\\[AUTOMATION:\\s*([^|\\]]+)\\|\\s*(\\d+)\\s*\\]([\\s\\S]*?)\\[/AUTOMATION\\]",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        matcher = autoPattern.matcher(remaining);
        lastEnd = 0;
        while (matcher.find()) {
            cleanResponse.append(remaining, lastEnd, matcher.start());
            lastEnd = matcher.end();
            String autoName = matcher.group(1).trim();
            int interval = Integer.parseInt(matcher.group(2).trim());
            String autoContent = matcher.group(3).trim();
            
            List<String> commands = new ArrayList<>();
            for (String line : autoContent.split("\\n")) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    if (line.startsWith("/")) line = line.substring(1);
                    commands.add(line);
                }
            }
            createAutomation(autoName, interval, commands);
        }
        cleanResponse.append(remaining.substring(lastEnd));
        remaining = cleanResponse.toString();
        
        // Pattern: [STOPAUTO: name]
        cleanResponse = new StringBuilder();
        java.util.regex.Pattern stopAutoPattern = java.util.regex.Pattern.compile(
            "\\[STOPAUTO:\\s*([^\\]]+)\\]",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        matcher = stopAutoPattern.matcher(remaining);
        lastEnd = 0;
        while (matcher.find()) {
            cleanResponse.append(remaining, lastEnd, matcher.start());
            lastEnd = matcher.end();
            String autoName = matcher.group(1).trim();
            stopAutomation(autoName);
        }
        cleanResponse.append(remaining.substring(lastEnd));
        remaining = cleanResponse.toString();
        
        // Pattern: ```command\n/commands\n``` or ```\n/commands\n```
        cleanResponse = new StringBuilder();
        java.util.regex.Pattern codeBlockPattern = java.util.regex.Pattern.compile(
            "```(?:command|commands|mcfunction)?\\s*\\n([^`]+)```",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        
        matcher = codeBlockPattern.matcher(remaining);
        lastEnd = 0;
        
        while (matcher.find()) {
            cleanResponse.append(remaining, lastEnd, matcher.start());
            lastEnd = matcher.end();
            
            String[] lines = matcher.group(1).split("\\n");
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("/")) {
                    executeCommand(line);
                }
            }
        }
        cleanResponse.append(remaining.substring(lastEnd));
        
        return cleanResponse.toString().trim();
    }
    
    /**
     * Execute a command as OP (operator).
     * The command should start with /
     */
    public void executeCommand(String command) {
        if (command == null || command.isEmpty()) return;
        if (command.length() > 1024) {
            sendChatMessage("Command rejected: too long.");
            return;
        }
        
        // Remove leading / if present
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        command = command.trim();
        if (command.isEmpty()) return;
        
        // Safety check - block dangerous commands
        String root = extractCommandRoot(command);
        if (isDangerousCommandRoot(root)) {
            LOGGER.warn("Aeonis blocked dangerous command: /{}", command);
            sendChatMessage("I can't execute that command for safety reasons.");
            return;
        }
        
        LOGGER.info("Aeonis executing command: /{}", command);
        
        try {
            // Execute with OP permissions using the server's command dispatcher
            var dispatcher = minecraftServer.getCommands().getDispatcher();
            var source = minecraftServer.createCommandSourceStack()
                .withEntity(this)
                .withPosition(this.position())
                .withLevel((ServerLevel) this.level())
                .withPermission(PermissionSet.ALL_PERMISSIONS) // Full permissions
                .withSuppressedOutput();
            
            dispatcher.execute(command, source);
            
        } catch (Exception e) {
            LOGGER.error("Failed to execute command '{}': {}", command, e.getMessage());
            sendChatMessage("Command failed: " + e.getMessage());
        }
    }

    private static String extractCommandRoot(String command) {
        String trimmed = command.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) return "";
        int firstSpace = trimmed.indexOf(' ');
        String firstToken = firstSpace >= 0 ? trimmed.substring(0, firstSpace) : trimmed;
        int namespaceSep = firstToken.indexOf(':');
        if (namespaceSep >= 0 && namespaceSep < firstToken.length() - 1) {
            return firstToken.substring(namespaceSep + 1);
        }
        return firstToken;
    }

    private static boolean isDangerousCommandRoot(String root) {
        return root.equals("stop")
            || root.equals("reload")
            || root.equals("ban")
            || root.equals("ban-ip")
            || root.equals("kick")
            || root.equals("op")
            || root.equals("deop")
            || root.equals("whitelist")
            || root.equals("save-off")
            || root.equals("save-all");
    }
    
    /**
     * Execute multiple commands in sequence
     */
    public void executeCommands(List<String> commands) {
        for (String cmd : commands) {
            executeCommand(cmd);
        }
    }
    
    /**
     * Create and run a temporary mcfunction script
     */
    public void runScript(String scriptName, List<String> commands) {
        LOGGER.info("Aeonis running script '{}' with {} commands", scriptName, commands.size());
        
        // Execute commands immediately (no file creation needed in singleplayer)
        for (String cmd : commands) {
            executeCommand(cmd);
        }
    }
    
    // ================ SCRIPT SYSTEM ================
    
    /**
     * Create a new script file
     */
    public boolean createScript(String name, String content) {
        AeonisScriptManager manager = AeonisScriptManager.getInstance();
        if (manager == null) {
            sendChatMessage("Script system not initialized!");
            return false;
        }
        
        boolean success = manager.createScript(name, content, AeonisScriptManager.ScriptType.COMMAND);
        if (success) {
            sendChatMessage("Created script: " + name);
        }
        return success;
    }
    
    /**
     * Run a saved script
     */
    public boolean runSavedScript(String name) {
        AeonisScriptManager manager = AeonisScriptManager.getInstance();
        if (manager == null) {
            sendChatMessage("Script system not initialized!");
            return false;
        }
        
        boolean success = manager.runScript(name, this);
        if (!success) {
            sendChatMessage("Script not found: " + name);
        }
        return success;
    }
    
    /**
     * Create a custom command
     */
    public boolean createCustomCommand(String name, String description, List<String> commands) {
        AeonisScriptManager manager = AeonisScriptManager.getInstance();
        if (manager == null) {
            sendChatMessage("Script system not initialized!");
            return false;
        }
        
        boolean success = manager.createCustomCommand(name, description, commands);
        if (success) {
            sendChatMessage("Created custom command: !" + name);
        }
        return success;
    }
    
    /**
     * Execute a custom command
     */
    public boolean runCustomCommand(String name) {
        AeonisScriptManager manager = AeonisScriptManager.getInstance();
        if (manager == null) return false;
        
        return manager.executeCustomCommand(name, this);
    }
    
    /**
     * Create an automation task
     */
    public void createAutomation(String name, int intervalSeconds, List<String> commands) {
        AeonisScriptManager manager = AeonisScriptManager.getInstance();
        if (manager == null) {
            sendChatMessage("Script system not initialized!");
            return;
        }
        
        manager.createAutomation(name, intervalSeconds * 20, commands); // Convert to ticks
        sendChatMessage("Created automation: " + name + " (every " + intervalSeconds + "s)");
    }
    
    /**
     * Stop an automation
     */
    public void stopAutomation(String name) {
        AeonisScriptManager manager = AeonisScriptManager.getInstance();
        if (manager != null && manager.stopAutomation(name)) {
            sendChatMessage("Stopped automation: " + name);
        }
    }
    
    /**
     * List all scripts
     */
    public List<String> listScripts() {
        AeonisScriptManager manager = AeonisScriptManager.getInstance();
        if (manager == null) return List.of();
        return manager.listScripts();
    }
    
    /**
     * List all custom commands
     */
    public Map<String, String> listCustomCommands() {
        AeonisScriptManager manager = AeonisScriptManager.getInstance();
        if (manager == null) return Map.of();
        return manager.listCustomCommands();
    }
    
    /**
     * List all automations
     */
    public List<String> listAutomations() {
        AeonisScriptManager manager = AeonisScriptManager.getInstance();
        if (manager == null) return List.of();
        return manager.listAutomations();
    }

    private List<String> splitMessage(String message, int maxLength) {
        List<String> parts = new ArrayList<>();
        
        while (message.length() > maxLength) {
            int splitIndex = message.lastIndexOf(' ', maxLength);
            if (splitIndex == -1) splitIndex = maxLength;
            
            parts.add(message.substring(0, splitIndex).trim());
            message = message.substring(splitIndex).trim();
        }
        
        if (!message.isEmpty()) {
            parts.add(message);
        }
        
        return parts;
    }
    
    // ================ BRAIN ACCESS ================
    
    /**
     * Get the Aeonis AI brain (renamed to avoid conflict with LivingEntity.getBrain())
     */
    public AeonisBrain getAeonisBrain() {
        return aeonisBrain;
    }
    
    public UUID getOwnerUuid() {
        return ownerUuid;
    }
    
    // ================ NETWORK HELPERS ================
    
    /**
     * Broadcast entity spawn/despawn to all players
     * 
     * Uses reflection to create ClientboundPlayerInfoUpdatePacket without
     * triggering the connection.latency() null crash for fake players.
     */
    private static void broadcastPlayerInfo(AeonisAssistant assistant, boolean add) {
        MinecraftServer server = assistant.minecraftServer;
        
        if (add) {
            // Create player info packet using reflection (required for client to render player)
            ClientboundPlayerInfoUpdatePacket infoPacket = FakePlayerPacketUtil.createAddPlayerPacket(
                assistant.getUUID(),
                assistant.getGameProfile(),
                assistant.getDisplayName(),
                GameType.SURVIVAL
            );
            
            // Create spawn entity packet for the player
            ClientboundAddEntityPacket spawnPacket = new ClientboundAddEntityPacket(assistant, 0, assistant.blockPosition());
            
            // Head rotation
            ClientboundRotateHeadPacket headPacket = new ClientboundRotateHeadPacket(
                assistant,
                (byte) (assistant.getYHeadRot() * 256.0F / 360.0F)
            );
            
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                // Skip if player has no connection (shouldn't happen but be safe)
                if (player.connection == null) continue;
                
                // Send player info packet FIRST - client needs this to know the GameProfile/skin
                if (infoPacket != null) {
                    player.connection.send(infoPacket);
                } else {
                    LOGGER.warn("Could not create player info packet via reflection - Aeonis may not render");
                }
                
                // Then send spawn packet
                player.connection.send(spawnPacket);
                
                // Send entity data if we have any dirty values
                var dirtyData = assistant.getEntityData().packDirty();
                if (dirtyData != null && !dirtyData.isEmpty()) {
                    player.connection.send(new ClientboundSetEntityDataPacket(assistant.getId(), dirtyData));
                }
                player.connection.send(headPacket);
            }
            
            LOGGER.info("Aeonis spawn packets sent to all players");
        } else {
            // Remove entity from world
            ClientboundRemoveEntitiesPacket despawnPacket = new ClientboundRemoveEntitiesPacket(assistant.getId());
            
            // Remove from player list
            ClientboundPlayerInfoRemovePacket removeInfoPacket = new ClientboundPlayerInfoRemovePacket(List.of(assistant.getUUID()));
            
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                // Skip if player has no connection
                if (player.connection == null) continue;
                
                player.connection.send(despawnPacket);
                player.connection.send(removeInfoPacket);
            }
            
            LOGGER.info("Aeonis despawn packets sent to all players");
        }
    }
    
    /**
     * Send position update to all players
     */
    public void broadcastPositionUpdate() {
        MinecraftServer server = this.minecraftServer;
        
        ClientboundTeleportEntityPacket teleportPacket = ClientboundTeleportEntityPacket.teleport(
            this.getId(),
            net.minecraft.world.entity.PositionMoveRotation.of(this),
            Set.of(),
            this.onGround()
        );
        
        ClientboundRotateHeadPacket headPacket = new ClientboundRotateHeadPacket(
            this,
            (byte) (this.getYHeadRot() * 256.0F / 360.0F)
        );
        
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // Skip if player has no connection
            if (player.connection == null) continue;
            
            player.connection.send(teleportPacket);
            player.connection.send(headPacket);
        }
    }
    
    // ================ OVERRIDES ================
    
    @Override
    public boolean isSpectator() {
        return false;
    }
    
    @Override
    public boolean isCreative() {
        return false;
    }
}
