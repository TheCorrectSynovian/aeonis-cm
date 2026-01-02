package com.qc.aeonis.llm.script;

import com.qc.aeonis.llm.AeonisAssistant;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Aeonis scripts - allows the AI to create, save, load, and run scripts.
 * Scripts can be:
 * - Command scripts (.mcfunction style)
 * - Behavior scripts (custom actions)
 * - Automation scripts (scheduled tasks)
 */
public class AeonisScriptManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("aeonis-script");
    
    private static AeonisScriptManager instance;
    
    private final Path scriptsDir;
    private final Path automationDir;
    private final Path customCommandsDir;
    
    // Loaded scripts cache
    private final Map<String, AeonisScript> scripts = new ConcurrentHashMap<>();
    
    // Custom commands created by AI/user
    private final Map<String, CustomCommand> customCommands = new ConcurrentHashMap<>();
    
    // Scheduled automation tasks
    private final List<AutomationTask> automationTasks = new ArrayList<>();
    
    public static AeonisScriptManager getInstance() {
        return instance;
    }
    
    public static void initialize(MinecraftServer server) {
        Path gameDir = server.getServerDirectory();
        instance = new AeonisScriptManager(gameDir);
        instance.loadAllScripts();
        instance.loadCustomCommands();
        LOGGER.info("Aeonis Script Manager initialized at {}", instance.scriptsDir);
    }
    
    private AeonisScriptManager(Path gameDir) {
        this.scriptsDir = gameDir.resolve("aeonis").resolve("scripts");
        this.automationDir = gameDir.resolve("aeonis").resolve("automation");
        this.customCommandsDir = gameDir.resolve("aeonis").resolve("commands");
        
        // Create directories if they don't exist
        try {
            Files.createDirectories(scriptsDir);
            Files.createDirectories(automationDir);
            Files.createDirectories(customCommandsDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create script directories: {}", e.getMessage());
        }
    }
    
    // ================ SCRIPT CREATION ================
    
    /**
     * Create a new script with the given name and content.
     * Returns true if successful.
     */
    public boolean createScript(String name, String content, ScriptType type) {
        // Sanitize name
        name = sanitizeName(name);
        if (name.isEmpty()) {
            LOGGER.warn("Invalid script name");
            return false;
        }
        
        String extension = type.getExtension();
        Path scriptPath = scriptsDir.resolve(name + extension);
        
        try {
            Files.writeString(scriptPath, content, StandardCharsets.UTF_8);
            
            // Cache it
            AeonisScript script = new AeonisScript(name, content, type, scriptPath);
            scripts.put(name, script);
            
            LOGGER.info("Created script: {} ({})", name, type);
            return true;
            
        } catch (IOException e) {
            LOGGER.error("Failed to create script '{}': {}", name, e.getMessage());
            return false;
        }
    }
    
    /**
     * Append content to an existing script or create if doesn't exist.
     */
    public boolean appendToScript(String name, String content) {
        name = sanitizeName(name);
        AeonisScript script = scripts.get(name);
        
        if (script == null) {
            // Create new
            return createScript(name, content, ScriptType.COMMAND);
        }
        
        try {
            String newContent = script.getContent() + "\n" + content;
            Files.writeString(script.getPath(), newContent, StandardCharsets.UTF_8);
            script.setContent(newContent);
            
            LOGGER.info("Appended to script: {}", name);
            return true;
            
        } catch (IOException e) {
            LOGGER.error("Failed to append to script '{}': {}", name, e.getMessage());
            return false;
        }
    }
    
    /**
     * Run a script by name
     */
    public boolean runScript(String name, AeonisAssistant assistant) {
        name = sanitizeName(name);
        AeonisScript script = scripts.get(name);
        
        if (script == null) {
            LOGGER.warn("Script not found: {}", name);
            return false;
        }
        
        return script.execute(assistant);
    }
    
    /**
     * Delete a script
     */
    public boolean deleteScript(String name) {
        name = sanitizeName(name);
        AeonisScript script = scripts.remove(name);
        
        if (script == null) {
            return false;
        }
        
        try {
            Files.deleteIfExists(script.getPath());
            LOGGER.info("Deleted script: {}", name);
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to delete script '{}': {}", name, e.getMessage());
            return false;
        }
    }
    
    /**
     * List all available scripts
     */
    public List<String> listScripts() {
        return new ArrayList<>(scripts.keySet());
    }
    
    /**
     * Get script content
     */
    public String getScriptContent(String name) {
        name = sanitizeName(name);
        AeonisScript script = scripts.get(name);
        return script != null ? script.getContent() : null;
    }
    
    // ================ CUSTOM COMMANDS ================
    
    /**
     * Create a custom command that can be invoked by the AI or user.
     * @param commandName The command name (e.g., "buildhouse")
     * @param description Description of what the command does
     * @param commands List of commands to execute
     */
    public boolean createCustomCommand(String commandName, String description, List<String> commands) {
        commandName = sanitizeName(commandName).toLowerCase();
        if (commandName.isEmpty()) return false;
        
        CustomCommand cmd = new CustomCommand(commandName, description, commands);
        customCommands.put(commandName, cmd);
        
        // Save to file
        saveCustomCommand(cmd);
        
        LOGGER.info("Created custom command: {}", commandName);
        return true;
    }
    
    /**
     * Execute a custom command
     */
    public boolean executeCustomCommand(String commandName, AeonisAssistant assistant) {
        commandName = sanitizeName(commandName).toLowerCase();
        CustomCommand cmd = customCommands.get(commandName);
        
        if (cmd == null) {
            return false;
        }
        
        LOGGER.info("Executing custom command: {}", commandName);
        for (String command : cmd.getCommands()) {
            assistant.executeCommand(command);
        }
        return true;
    }
    
    /**
     * Check if a custom command exists
     */
    public boolean hasCustomCommand(String commandName) {
        return customCommands.containsKey(sanitizeName(commandName).toLowerCase());
    }
    
    /**
     * List all custom commands
     */
    public Map<String, String> listCustomCommands() {
        Map<String, String> result = new HashMap<>();
        for (CustomCommand cmd : customCommands.values()) {
            result.put(cmd.getName(), cmd.getDescription());
        }
        return result;
    }
    
    /**
     * Delete a custom command
     */
    public boolean deleteCustomCommand(String commandName) {
        commandName = sanitizeName(commandName).toLowerCase();
        CustomCommand cmd = customCommands.remove(commandName);
        
        if (cmd == null) return false;
        
        try {
            Files.deleteIfExists(customCommandsDir.resolve(commandName + ".json"));
            LOGGER.info("Deleted custom command: {}", commandName);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    // ================ AUTOMATION ================
    
    /**
     * Create an automation task that runs periodically
     * @param name Task name
     * @param intervalTicks How often to run (in game ticks, 20 = 1 second)
     * @param commands Commands to execute
     */
    public void createAutomation(String name, int intervalTicks, List<String> commands) {
        name = sanitizeName(name);
        AutomationTask task = new AutomationTask(name, intervalTicks, commands);
        automationTasks.add(task);
        saveAutomation(task);
        LOGGER.info("Created automation: {} (every {} ticks)", name, intervalTicks);
    }
    
    /**
     * Stop and remove an automation
     */
    public boolean stopAutomation(String name) {
        name = sanitizeName(name);
        String finalName = name;
        boolean removed = automationTasks.removeIf(t -> t.getName().equals(finalName));
        
        if (removed) {
            try {
                Files.deleteIfExists(automationDir.resolve(name + ".json"));
            } catch (IOException e) {
                // Ignore
            }
        }
        return removed;
    }
    
    /**
     * Tick all automation tasks
     */
    public void tickAutomation(AeonisAssistant assistant) {
        for (AutomationTask task : automationTasks) {
            if (task.shouldRun()) {
                for (String cmd : task.getCommands()) {
                    assistant.executeCommand(cmd);
                }
                task.resetTimer();
            } else {
                task.tick();
            }
        }
    }
    
    /**
     * List all automations
     */
    public List<String> listAutomations() {
        List<String> names = new ArrayList<>();
        for (AutomationTask task : automationTasks) {
            names.add(task.getName());
        }
        return names;
    }
    
    // ================ PERSISTENCE ================
    
    private void loadAllScripts() {
        try {
            if (!Files.exists(scriptsDir)) return;
            
            Files.list(scriptsDir).forEach(path -> {
                try {
                    String filename = path.getFileName().toString();
                    String name = filename.substring(0, filename.lastIndexOf('.'));
                    String content = Files.readString(path, StandardCharsets.UTF_8);
                    
                    ScriptType type = ScriptType.fromExtension(filename);
                    AeonisScript script = new AeonisScript(name, content, type, path);
                    scripts.put(name, script);
                    
                } catch (IOException e) {
                    LOGGER.error("Failed to load script: {}", path);
                }
            });
            
            LOGGER.info("Loaded {} scripts", scripts.size());
            
        } catch (IOException e) {
            LOGGER.error("Failed to load scripts: {}", e.getMessage());
        }
    }
    
    private void loadCustomCommands() {
        try {
            if (!Files.exists(customCommandsDir)) return;
            
            Files.list(customCommandsDir)
                .filter(p -> p.toString().endsWith(".json"))
                .forEach(path -> {
                    try {
                        String json = Files.readString(path, StandardCharsets.UTF_8);
                        CustomCommand cmd = CustomCommand.fromJson(json);
                        if (cmd != null) {
                            customCommands.put(cmd.getName(), cmd);
                        }
                    } catch (IOException e) {
                        LOGGER.error("Failed to load custom command: {}", path);
                    }
                });
            
            LOGGER.info("Loaded {} custom commands", customCommands.size());
            
        } catch (IOException e) {
            LOGGER.error("Failed to load custom commands: {}", e.getMessage());
        }
    }
    
    private void saveCustomCommand(CustomCommand cmd) {
        try {
            Path path = customCommandsDir.resolve(cmd.getName() + ".json");
            Files.writeString(path, cmd.toJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Failed to save custom command: {}", e.getMessage());
        }
    }
    
    private void saveAutomation(AutomationTask task) {
        try {
            Path path = automationDir.resolve(task.getName() + ".json");
            Files.writeString(path, task.toJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Failed to save automation: {}", e.getMessage());
        }
    }
    
    // ================ UTILITIES ================
    
    private String sanitizeName(String name) {
        if (name == null) return "";
        // Remove unsafe characters, keep alphanumeric and underscores
        return name.replaceAll("[^a-zA-Z0-9_-]", "").trim();
    }
    
    public Path getScriptsDirectory() {
        return scriptsDir;
    }
    
    // ================ INNER CLASSES ================
    
    public enum ScriptType {
        COMMAND(".mcfunction"),
        BEHAVIOR(".aescript"),
        AUTOMATION(".auto");
        
        private final String extension;
        
        ScriptType(String extension) {
            this.extension = extension;
        }
        
        public String getExtension() {
            return extension;
        }
        
        public static ScriptType fromExtension(String filename) {
            if (filename.endsWith(".mcfunction")) return COMMAND;
            if (filename.endsWith(".auto")) return AUTOMATION;
            return BEHAVIOR;
        }
    }
    
    /**
     * Represents a saved script
     */
    public static class AeonisScript {
        private final String name;
        private String content;
        private final ScriptType type;
        private final Path path;
        
        public AeonisScript(String name, String content, ScriptType type, Path path) {
            this.name = name;
            this.content = content;
            this.type = type;
            this.path = path;
        }
        
        public boolean execute(AeonisAssistant assistant) {
            if (type == ScriptType.COMMAND) {
                // Execute as mcfunction
                String[] lines = content.split("\n");
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    
                    if (line.startsWith("/")) {
                        line = line.substring(1);
                    }
                    assistant.executeCommand(line);
                }
                return true;
            }
            // Other script types could be implemented here
            return false;
        }
        
        public String getName() { return name; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public ScriptType getType() { return type; }
        public Path getPath() { return path; }
    }
    
    /**
     * Represents a custom command
     */
    public static class CustomCommand {
        private final String name;
        private final String description;
        private final List<String> commands;
        
        public CustomCommand(String name, String description, List<String> commands) {
            this.name = name;
            this.description = description;
            this.commands = new ArrayList<>(commands);
        }
        
        public String getName() { return name; }
        public String getDescription() { return description; }
        public List<String> getCommands() { return commands; }
        
        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"name\": \"").append(name).append("\",\n");
            sb.append("  \"description\": \"").append(description.replace("\"", "\\\"")).append("\",\n");
            sb.append("  \"commands\": [\n");
            for (int i = 0; i < commands.size(); i++) {
                sb.append("    \"").append(commands.get(i).replace("\"", "\\\"")).append("\"");
                if (i < commands.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("  ]\n");
            sb.append("}");
            return sb.toString();
        }
        
        public static CustomCommand fromJson(String json) {
            try {
                // Simple JSON parsing
                String name = extractJsonString(json, "name");
                String description = extractJsonString(json, "description");
                List<String> commands = extractJsonArray(json, "commands");
                
                if (name != null && commands != null) {
                    return new CustomCommand(name, description != null ? description : "", commands);
                }
            } catch (Exception e) {
                // Parse error
            }
            return null;
        }
        
        private static String extractJsonString(String json, String key) {
            int idx = json.indexOf("\"" + key + "\"");
            if (idx < 0) return null;
            int start = json.indexOf("\"", idx + key.length() + 3) + 1;
            int end = json.indexOf("\"", start);
            if (start > 0 && end > start) {
                return json.substring(start, end).replace("\\\"", "\"");
            }
            return null;
        }
        
        private static List<String> extractJsonArray(String json, String key) {
            int idx = json.indexOf("\"" + key + "\"");
            if (idx < 0) return null;
            int start = json.indexOf("[", idx) + 1;
            int end = json.indexOf("]", start);
            if (start > 0 && end > start) {
                String arrayContent = json.substring(start, end);
                List<String> result = new ArrayList<>();
                
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"([^\"]+)\"");
                java.util.regex.Matcher matcher = pattern.matcher(arrayContent);
                while (matcher.find()) {
                    result.add(matcher.group(1).replace("\\\"", "\""));
                }
                return result;
            }
            return null;
        }
    }
    
    /**
     * Represents an automation task
     */
    public static class AutomationTask {
        private final String name;
        private final int intervalTicks;
        private final List<String> commands;
        private int currentTick = 0;
        
        public AutomationTask(String name, int intervalTicks, List<String> commands) {
            this.name = name;
            this.intervalTicks = intervalTicks;
            this.commands = new ArrayList<>(commands);
        }
        
        public void tick() {
            currentTick++;
        }
        
        public boolean shouldRun() {
            return currentTick >= intervalTicks;
        }
        
        public void resetTimer() {
            currentTick = 0;
        }
        
        public String getName() { return name; }
        public int getIntervalTicks() { return intervalTicks; }
        public List<String> getCommands() { return commands; }
        
        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"name\": \"").append(name).append("\",\n");
            sb.append("  \"intervalTicks\": ").append(intervalTicks).append(",\n");
            sb.append("  \"commands\": [\n");
            for (int i = 0; i < commands.size(); i++) {
                sb.append("    \"").append(commands.get(i).replace("\"", "\\\"")).append("\"");
                if (i < commands.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("  ]\n");
            sb.append("}");
            return sb.toString();
        }
    }
}
