package com.qc.aeonis.screen

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * ╔════════════════════════════════════════════════════════════════════════════╗
 * ║                      MANHUNT SETUP SCREEN                                  ║
 * ║                                                                            ║
 * ║  GUI for configuring and starting a Manhunt game.                          ║
 * ║  Allows setting:                                                           ║
 * ║  - Difficulty (Easy/Normal/Hard/Nightmare)                                 ║
 * ║  - Head start time                                                         ║
 * ║  - Respawn settings                                                        ║
 * ╚════════════════════════════════════════════════════════════════════════════╝
 */
class ManhuntSetupScreen : Screen(Component.literal("Manhunt Setup")) {
    
    // Settings
    private var selectedDifficulty = Difficulty.NORMAL
    private var headStartSeconds = 30
    private var maxRespawns = 3
    private var hunterBlocks = 64
    
    // UI components
    private var difficultyButton: CycleButton<Difficulty>? = null
    private var headStartButton: Button? = null
    private var respawnButton: Button? = null
    private var blocksButton: Button? = null
    
    enum class Difficulty(val displayName: String, val value: Float, val color: String) {
        EASY("Easy", 0.5f, "§a"),
        NORMAL("Normal", 1.0f, "§e"),
        HARD("Hard", 1.5f, "§c"),
        NIGHTMARE("Nightmare", 2.0f, "§4")
    }
    
    override fun init() {
        super.init()
        
        val centerX = width / 2
        val startY = height / 2 - 80
        val buttonWidth = 200
        val buttonHeight = 20
        val spacing = 28
        
        // Difficulty selector
        difficultyButton = CycleButton.builder<Difficulty>(
            { diff -> Component.literal("Difficulty: ${diff.color}${diff.displayName}") },
            selectedDifficulty
        )
            .withValues(*Difficulty.entries.toTypedArray())
            .create(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight, Component.literal("Difficulty")) { _, value ->
                selectedDifficulty = value
                updateSettingsForDifficulty()
            }
        addRenderableWidget(difficultyButton!!)
        
        // Head start time
        headStartButton = Button.builder(Component.literal("Head Start: §e${headStartSeconds}s")) { _ ->
            // Cycle through values
            headStartSeconds = when (headStartSeconds) {
                10 -> 20
                20 -> 30
                30 -> 45
                45 -> 60
                60 -> 90
                90 -> 120
                else -> 10
            }
            headStartButton?.message = Component.literal("Head Start: §e${headStartSeconds}s")
        }.bounds(centerX - buttonWidth / 2, startY + spacing, buttonWidth, buttonHeight).build()
        addRenderableWidget(headStartButton!!)
        
        // Respawns
        respawnButton = Button.builder(Component.literal("Max Respawns: §e$maxRespawns")) { _ ->
            maxRespawns = when (maxRespawns) {
                0 -> 1
                1 -> 3
                3 -> 5
                5 -> 10
                10 -> -1
                else -> 0
            }
            val respawnText = if (maxRespawns < 0) "Unlimited" else maxRespawns.toString()
            respawnButton?.message = Component.literal("Max Respawns: §e$respawnText")
        }.bounds(centerX - buttonWidth / 2, startY + spacing * 2, buttonWidth, buttonHeight).build()
        addRenderableWidget(respawnButton!!)
        
        // Hunter blocks
        blocksButton = Button.builder(Component.literal("Hunter Blocks: §e$hunterBlocks")) { _ ->
            hunterBlocks = when (hunterBlocks) {
                32 -> 64
                64 -> 96
                96 -> 128
                128 -> 192
                192 -> 256
                else -> 32
            }
            blocksButton?.message = Component.literal("Hunter Blocks: §e$hunterBlocks")
        }.bounds(centerX - buttonWidth / 2, startY + spacing * 3, buttonWidth, buttonHeight).build()
        addRenderableWidget(blocksButton!!)
        
        // Create and Start button
        val createButton = Button.builder(Component.literal("§a§lCreate & Start Game")) { _ ->
            createGame()
        }.bounds(centerX - buttonWidth / 2, startY + spacing * 5, buttonWidth, buttonHeight + 5).build()
        addRenderableWidget(createButton)
        
        // Just Create button (for setup)
        val setupButton = Button.builder(Component.literal("§eCreate Game (Setup)")) { _ ->
            createGameSetupOnly()
        }.bounds(centerX - buttonWidth / 2, startY + spacing * 6 + 5, buttonWidth, buttonHeight).build()
        addRenderableWidget(setupButton)
        
        // Cancel button
        val cancelButton = Button.builder(Component.literal("§7Cancel")) { _ ->
            onClose()
        }.bounds(centerX - buttonWidth / 2, startY + spacing * 7 + 10, buttonWidth, buttonHeight).build()
        addRenderableWidget(cancelButton)
    }
    
    private fun updateSettingsForDifficulty() {
        when (selectedDifficulty) {
            Difficulty.EASY -> {
                headStartSeconds = 60
                maxRespawns = 5
                hunterBlocks = 32
            }
            Difficulty.NORMAL -> {
                headStartSeconds = 30
                maxRespawns = 3
                hunterBlocks = 64
            }
            Difficulty.HARD -> {
                headStartSeconds = 20
                maxRespawns = 1
                hunterBlocks = 96
            }
            Difficulty.NIGHTMARE -> {
                headStartSeconds = 10
                maxRespawns = 0
                hunterBlocks = 128
            }
        }
        
        // Update button labels
        headStartButton?.message = Component.literal("Head Start: §e${headStartSeconds}s")
        val respawnText = if (maxRespawns < 0) "Unlimited" else maxRespawns.toString()
        respawnButton?.message = Component.literal("Max Respawns: §e$respawnText")
        blocksButton?.message = Component.literal("Hunter Blocks: §e$hunterBlocks")
    }
    
    private fun createGame() {
        val mc = Minecraft.getInstance()
        mc.player?.let { player ->
            // Send chat command with settings
            player.connection.sendCommand(
                "manhunt settings difficulty ${selectedDifficulty.value}"
            )
            player.connection.sendCommand(
                "manhunt settings headstart $headStartSeconds"
            )
            player.connection.sendCommand(
                "manhunt settings respawns $maxRespawns"
            )
            player.connection.sendCommand(
                "manhunt settings blocks $hunterBlocks"
            )
            // Create and start
            player.connection.sendCommand("manhunt create ${selectedDifficulty.name.lowercase()}")
            
            // Delay the start slightly
            mc.execute {
                player.connection.sendCommand("manhunt start")
            }
        }
        onClose()
    }
    
    private fun createGameSetupOnly() {
        val mc = Minecraft.getInstance()
        mc.player?.let { player ->
            // Send chat command with settings
            player.connection.sendCommand(
                "manhunt settings difficulty ${selectedDifficulty.value}"
            )
            player.connection.sendCommand(
                "manhunt settings headstart $headStartSeconds"
            )
            player.connection.sendCommand(
                "manhunt settings respawns $maxRespawns"
            )
            player.connection.sendCommand(
                "manhunt settings blocks $hunterBlocks"
            )
            // Just create, don't start
            player.connection.sendCommand("manhunt create ${selectedDifficulty.name.lowercase()}")
        }
        onClose()
    }
    
    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Render background
        renderBackground(graphics, mouseX, mouseY, partialTick)
        
        // Render title
        graphics.drawCenteredString(
            font,
            "§6§l═══════ MANHUNT SETUP ═══════",
            width / 2,
            height / 2 - 110,
            0xFFFFFF
        )
        
        // Render description
        graphics.drawCenteredString(
            font,
            "§7Configure your manhunt game settings",
            width / 2,
            height / 2 - 95,
            0xAAAAAA
        )
        
        // Difficulty description
        val diffDesc = when (selectedDifficulty) {
            Difficulty.EASY -> "§7Slow hunter, more respawns, long head start"
            Difficulty.NORMAL -> "§7Balanced gameplay, standard settings"
            Difficulty.HARD -> "§7Fast hunter, few respawns, short head start"
            Difficulty.NIGHTMARE -> "§4Relentless hunter, no respawns, minimal head start!"
        }
        graphics.drawCenteredString(font, diffDesc, width / 2, height / 2 - 50, 0xAAAAAA)
        
        super.render(graphics, mouseX, mouseY, partialTick)
    }
    
    override fun isPauseScreen(): Boolean = false
}
