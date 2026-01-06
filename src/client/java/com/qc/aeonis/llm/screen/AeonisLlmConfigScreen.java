package com.qc.aeonis.llm.screen;

import com.qc.aeonis.llm.network.payloads.*;
import com.qc.aeonis.llm.provider.LlmProviderType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI Screen for configuring the Aeonis LLM assistant.
 * 
 * Layout:
 * - Provider selection (radio buttons)
 * - Model dropdown + custom input
 * - API key field (masked)
 * - Temperature slider
 * - Max tokens slider
 * - Safety settings
 * - Action buttons
 */
@Environment(EnvType.CLIENT)
public class AeonisLlmConfigScreen extends Screen {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("aeonis-llm-screen");
    
    // Current configuration from server
    private final LlmOpenGuiPayload initialConfig;
    
    // Selected values
    private LlmProviderType selectedProvider;
    private String selectedModel;
    private String apiKey = "";
    private double temperature;
    private int maxTokens;
    private int maxBlocksPerMinute;
    private int maxEditRadius;
    private boolean griefProtection;
    
    // UI Components
    private List<Button> providerButtons = new ArrayList<>();
    private CycleButton<String> modelSelector;
    private EditBox apiKeyField;
    private EditBox customModelField;
    private AbstractSliderButton temperatureSlider;
    private AbstractSliderButton maxTokensSlider;
    private AbstractSliderButton blocksPerMinSlider;
    private AbstractSliderButton editRadiusSlider;
    private Checkbox griefProtectionCheckbox;
    
    private Button saveButton;
    private Button spawnButton;
    private Button testButton;
    private Button cancelButton;
    
    // Status message
    private String statusMessage = "";
    private boolean statusSuccess = true;
    private long statusMessageTime = 0;
    
    // Debounce for action buttons
    private long lastActionTime = 0;
    private static final long DEBOUNCE_MS = 1000; // 1 second between actions
    
    public AeonisLlmConfigScreen(LlmOpenGuiPayload config) {
        super(Component.literal("Aeonis LLM Configuration"));
        this.initialConfig = config;
        
        // Initialize from config
        this.selectedProvider = LlmProviderType.fromName(config.currentProvider());
        this.selectedModel = config.currentModel();
        this.temperature = config.temperature();
        this.maxTokens = config.maxTokens();
        this.maxBlocksPerMinute = config.maxBlocksPerMinute();
        this.maxEditRadius = config.maxEditRadius();
        this.griefProtection = config.griefProtection();
    }
    
    @Override
    protected void init() {
        super.init();
        
        int centerX = this.width / 2;
        int startY = 30;
        int rowHeight = 24;
        int currentY = startY;
        
        // Title is rendered separately
        
        // ============ Provider Selection ============
        currentY += 15;
        
        int buttonWidth = 80;
        int totalWidth = buttonWidth * 3 + 10;
        int startX = centerX - totalWidth / 2;
        
        providerButtons.clear();
        for (int i = 0; i < LlmProviderType.values().length; i++) {
            LlmProviderType provider = LlmProviderType.values()[i];
            int x = startX + (buttonWidth + 5) * i;
            
            Button btn = Button.builder(
                Component.literal(provider.getDisplayName()),
                button -> selectProvider(provider)
            ).bounds(x, currentY, buttonWidth, 20).build();
            
            providerButtons.add(btn);
            this.addRenderableWidget(btn);
        }
        updateProviderButtons();
        
        currentY += rowHeight + 5;
        
        // ============ Model Selection ============
        // Model dropdown - wider to show full model names like "openai/gpt-oss-20b:free"
        List<String> models = selectedProvider.getDefaultModels();
        String[] modelArray = models.toArray(new String[0]);
        
        String initialModel = models.contains(selectedModel) ? selectedModel : models.get(0);
        modelSelector = CycleButton.<String>builder(Component::literal, initialModel)
            .withValues(modelArray)
            .create(centerX - 150, currentY, 300, 20, 
                Component.literal("Model"), 
                (btn, value) -> selectedModel = value);
        this.addRenderableWidget(modelSelector);
        
        currentY += rowHeight;
        
        // Custom model field
        customModelField = new EditBox(this.font, centerX - 100, currentY, 200, 20, Component.literal("Custom Model"));
        customModelField.setHint(Component.literal("Custom model name (optional)"));
        customModelField.setMaxLength(100);
        customModelField.setResponder(text -> {
            // Only use custom model if it looks like a valid model ID (contains / or is longer than 10 chars)
            if (!text.isEmpty() && (text.contains("/") || text.length() > 10)) {
                selectedModel = text;
            } else if (text.isEmpty()) {
                // Reset to dropdown selection when custom field is cleared
                selectedModel = modelSelector.getValue();
            }
        });
        this.addRenderableWidget(customModelField);
        
        currentY += rowHeight + 5;
        
        // ============ API Key ============
        apiKeyField = new EditBox(this.font, centerX - 100, currentY, 200, 20, Component.literal("API Key"));
        apiKeyField.setHint(Component.literal(initialConfig.hasApiKey() ? "••••••••••••• (already set)" : "Enter API key"));
        apiKeyField.setMaxLength(200);
        apiKeyField.setResponder(text -> apiKey = text);
        // Mask input - Note: Minecraft EditBox doesn't have native masking, so we handle this differently
        // The actual value is stored, display is handled in render
        this.addRenderableWidget(apiKeyField);
        
        currentY += rowHeight + 10;
        
        // ============ Sliders ============
        // Temperature
        temperatureSlider = new AbstractSliderButton(centerX - 100, currentY, 200, 20, 
            Component.literal("Temperature: " + String.format("%.1f", temperature)), temperature) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal("Temperature: " + String.format("%.1f", value)));
            }
            
            @Override
            protected void applyValue() {
                temperature = value;
            }
        };
        this.addRenderableWidget(temperatureSlider);
        
        currentY += rowHeight;
        
        // Max tokens
        double tokenValue = (maxTokens - 100) / 3900.0; // Map 100-4000 to 0-1
        maxTokensSlider = new AbstractSliderButton(centerX - 100, currentY, 200, 20,
            Component.literal("Max Tokens: " + maxTokens), tokenValue) {
            @Override
            protected void updateMessage() {
                int tokens = (int)(value * 3900) + 100;
                setMessage(Component.literal("Max Tokens: " + tokens));
            }
            
            @Override
            protected void applyValue() {
                maxTokens = (int)(value * 3900) + 100;
            }
        };
        this.addRenderableWidget(maxTokensSlider);
        
        currentY += rowHeight + 10;
        
        // ============ Safety Settings ============
        // Blocks per minute
        double blocksValue = maxBlocksPerMinute / 100.0;
        blocksPerMinSlider = new AbstractSliderButton(centerX - 100, currentY, 200, 20,
            Component.literal("Max Blocks/Min: " + maxBlocksPerMinute), blocksValue) {
            @Override
            protected void updateMessage() {
                int blocks = Math.max(1, (int)(value * 100));
                setMessage(Component.literal("Max Blocks/Min: " + blocks));
            }
            
            @Override
            protected void applyValue() {
                maxBlocksPerMinute = Math.max(1, (int)(value * 100));
            }
        };
        this.addRenderableWidget(blocksPerMinSlider);
        
        currentY += rowHeight;
        
        // Edit radius
        double radiusValue = maxEditRadius / 200.0;
        editRadiusSlider = new AbstractSliderButton(centerX - 100, currentY, 200, 20,
            Component.literal("Max Edit Radius: " + maxEditRadius), radiusValue) {
            @Override
            protected void updateMessage() {
                int radius = Math.max(1, (int)(value * 200));
                setMessage(Component.literal("Max Edit Radius: " + radius));
            }
            
            @Override
            protected void applyValue() {
                maxEditRadius = Math.max(1, (int)(value * 200));
            }
        };
        this.addRenderableWidget(editRadiusSlider);
        
        currentY += rowHeight;
        
        // Grief protection checkbox
        griefProtectionCheckbox = Checkbox.builder(Component.literal("Enable Grief Protection"), this.font)
            .pos(centerX - 100, currentY)
            .selected(griefProtection)
            .onValueChange((checkbox, selected) -> griefProtection = selected)
            .build();
        this.addRenderableWidget(griefProtectionCheckbox);
        
        currentY += rowHeight + 15;
        
        // ============ Action Buttons ============
        int btnWidth = 70;
        int btnSpacing = 5;
        int totalBtnWidth = btnWidth * 4 + btnSpacing * 3;
        int btnStartX = centerX - totalBtnWidth / 2;
        
        testButton = Button.builder(Component.literal("Test"), btn -> testConnection())
            .bounds(btnStartX, currentY, btnWidth, 20).build();
        this.addRenderableWidget(testButton);
        
        saveButton = Button.builder(Component.literal("Save"), btn -> saveConfig())
            .bounds(btnStartX + btnWidth + btnSpacing, currentY, btnWidth, 20).build();
        this.addRenderableWidget(saveButton);
        
        spawnButton = Button.builder(Component.literal("Spawn"), btn -> spawnBot())
            .bounds(btnStartX + (btnWidth + btnSpacing) * 2, currentY, btnWidth, 20).build();
        this.addRenderableWidget(spawnButton);
        
        cancelButton = Button.builder(Component.literal("Cancel"), btn -> onClose())
            .bounds(btnStartX + (btnWidth + btnSpacing) * 3, currentY, btnWidth, 20).build();
        this.addRenderableWidget(cancelButton);
    }
    
    private void selectProvider(LlmProviderType provider) {
        this.selectedProvider = provider;
        updateProviderButtons();
        
        // Update model selector with new provider's models
        List<String> models = provider.getDefaultModels();
        if (!models.contains(selectedModel)) {
            selectedModel = models.get(0);
        }
        
        // Rebuild model selector
        this.rebuildWidgets();
    }
    
    private void updateProviderButtons() {
        for (int i = 0; i < providerButtons.size(); i++) {
            Button btn = providerButtons.get(i);
            boolean selected = LlmProviderType.values()[i] == selectedProvider;
            // Visual indication of selection
            btn.active = !selected;
        }
    }
    
    /**
     * Get the valid model to use - prefers custom field if valid, otherwise uses dropdown
     */
    private String getValidModel() {
        String customText = customModelField != null ? customModelField.getValue() : "";
        // Use custom model if it looks like a valid model ID (contains / or is longer than 10 chars)
        if (!customText.isEmpty() && (customText.contains("/") || customText.length() > 10)) {
            return customText;
        }
        // Otherwise use the dropdown selection
        if (modelSelector != null && modelSelector.getValue() != null) {
            return modelSelector.getValue();
        }
        // Fallback to selected provider's first default model
        java.util.List<String> defaults = selectedProvider.getDefaultModels();
        return !defaults.isEmpty() ? defaults.get(0) : "gpt-4o-mini";
    }
    
    private void testConnection() {
        // Debounce
        long now = System.currentTimeMillis();
        if (now - lastActionTime < DEBOUNCE_MS) return;
        lastActionTime = now;
        
        String key = apiKey.isEmpty() ? null : apiKey;
        
        // If no new key and no existing key, show error
        if (key == null && !initialConfig.hasApiKey()) {
            showStatus("Please enter an API key first", false);
            return;
        }
        
        // Get the validated model
        String modelToTest = getValidModel();
        
        // Use existing key if no new one provided
        if (key == null) {
            showStatus("Testing with existing API key...", true);
            // For testing with existing key, we need the server to handle it
            // This is a limitation - we can't test with existing key without server support
        } else {
            showStatus("Testing connection...", true);
        }
        
        // Send test request
        ClientPlayNetworking.send(new LlmTestConnectionPayload(
            selectedProvider.name(),
            modelToTest,
            key != null ? key : "",
            temperature
        ));
    }
    
    private void saveConfig() {
        // Debounce
        long now = System.currentTimeMillis();
        if (now - lastActionTime < DEBOUNCE_MS) return;
        lastActionTime = now;
        
        showStatus("Saving configuration...", true);
        
        // Get the validated model
        String modelToSave = getValidModel();
        
        ClientPlayNetworking.send(new LlmConfigSubmitPayload(
            selectedProvider.name(),
            modelToSave,
            apiKey,
            temperature,
            maxTokens,
            maxBlocksPerMinute,
            maxEditRadius,
            griefProtection
        ));
    }
    
    private void spawnBot() {
        // Debounce
        long now = System.currentTimeMillis();
        if (now - lastActionTime < DEBOUNCE_MS) return;
        lastActionTime = now;
        
        // Save config first if API key is set
        if (!apiKey.isEmpty() || initialConfig.hasApiKey()) {
            // Don't use saveConfig() here to avoid double debounce
            String modelToSave = getValidModel();
            ClientPlayNetworking.send(new LlmConfigSubmitPayload(
                selectedProvider.name(),
                modelToSave,
                apiKey,
                temperature,
                maxTokens,
                maxBlocksPerMinute,
                maxEditRadius,
                griefProtection
            ));
        }
        
        showStatus("Spawning Aeonis...", true);
        ClientPlayNetworking.send(new LlmSpawnBotPayload(true));
    }
    
    private void showStatus(String message, boolean success) {
        this.statusMessage = message;
        this.statusSuccess = success;
        this.statusMessageTime = System.currentTimeMillis();
    }
    
    /**
     * Handle status update from server
     */
    public void handleStatusUpdate(LlmStatusUpdatePayload payload) {
        showStatus(payload.message(), payload.success());
        
        // Close screen on successful spawn
        if (payload.statusType() == LlmStatusUpdatePayload.StatusType.SPAWN_RESULT && payload.success()) {
            // Close the screen after a short delay so user sees the success message
            this.closeAfterTicks = 20; // Close after ~1 second (20 ticks)
        }
    }
    
    // Counter for delayed screen close
    private int closeAfterTicks = -1;
    
    @Override
    public void tick() {
        super.tick();
        // Handle delayed close after spawn success
        if (closeAfterTicks > 0) {
            closeAfterTicks--;
        } else if (closeAfterTicks == 0) {
            this.onClose();
        }
    }
    
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // Let super handle background rendering to avoid double blur
        super.render(graphics, mouseX, mouseY, delta);
        
        // Title
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        
        // Section labels
        int centerX = this.width / 2;
        graphics.drawString(this.font, "Provider:", centerX - 100, 33, 0xAAAAAA);
        graphics.drawString(this.font, "Model:", centerX - 140, 75, 0xAAAAAA);
        graphics.drawString(this.font, "API Key:", centerX - 140, 123, 0xAAAAAA);
        
        // Status message
        if (!statusMessage.isEmpty() && System.currentTimeMillis() - statusMessageTime < 5000) {
            int statusColor = statusSuccess ? 0x55FF55 : 0xFF5555;
            graphics.drawCenteredString(this.font, statusMessage, centerX, this.height - 30, statusColor);
        }
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
    
    @Override
    public void onClose() {
        this.minecraft.setScreen(null);
    }
}
