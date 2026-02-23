package com.qc.aeonis.screen

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

@Environment(EnvType.CLIENT)
class AeonisPlaceholderConfigScreen(private val parent: Screen?) : Screen(Component.literal("Aeonis Config (Placeholder)")) {
    private lateinit var inputField: EditBox
    private lateinit var outputField: EditBox
    private var status: Component = Component.literal("Edit values and press Save.")
    private var statusColor: Int = 0xA6B7D4

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configPath: Path = FabricLoader.getInstance().configDir.resolve("aeonis-placeholder-io.json")

    override fun init() {
        super.init()

        val boxWidth = 320
        val boxX = (width - boxWidth) / 2
        val centerY = height / 2

        val loaded = loadConfig()
        val inputValue = loaded.first
        val outputValue = loaded.second

        inputField = EditBox(font, boxX, centerY - 26, boxWidth, 20, Component.literal("Input endpoint"))
        inputField.setMaxLength(256)
        inputField.setValue(inputValue)
        addRenderableWidget(inputField)

        outputField = EditBox(font, boxX, centerY + 6, boxWidth, 20, Component.literal("Output channel"))
        outputField.setMaxLength(256)
        outputField.setValue(outputValue)
        addRenderableWidget(outputField)

        addRenderableWidget(
            Button.builder(Component.literal("Save")) {
                saveConfig(inputField.value.trim(), outputField.value.trim())
            }.bounds(boxX, centerY + 36, 100, 20).build()
        )

        addRenderableWidget(
            Button.builder(Component.literal("Reset")) {
                inputField.setValue("placeholder://input")
                outputField.setValue("placeholder://output")
                status = Component.literal("Reset to placeholder values. Press Save to persist.")
                statusColor = 0xE2C86A
            }.bounds(boxX + 110, centerY + 36, 100, 20).build()
        )

        addRenderableWidget(
            Button.builder(Component.literal("Done")) {
                onClose()
            }.bounds(boxX + 220, centerY + 36, 100, 20).build()
        )
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics, mouseX, mouseY, partialTick)
        super.render(graphics, mouseX, mouseY, partialTick)

        val boxX = (width - 320) / 2
        val centerY = height / 2

        graphics.drawCenteredString(font, title, width / 2, centerY - 70, 0xFFFFFF)
        graphics.drawString(font, Component.literal("Input (placeholder I/O key)"), boxX, centerY - 38, 0xC7D2E7)
        graphics.drawString(font, Component.literal("Output (placeholder route)"), boxX, centerY - 6, 0xC7D2E7)
        graphics.drawString(font, Component.literal("File: ${configPath.fileName}"), boxX, centerY + 66, 0x7F93B5)
        graphics.drawCenteredString(font, status, width / 2, centerY + 84, statusColor)
    }

    override fun onClose() {
        minecraft?.setScreen(parent)
    }

    private fun loadConfig(): Pair<String, String> {
        return try {
            if (!Files.exists(configPath)) {
                return "placeholder://input" to "placeholder://output"
            }
            val root = gson.fromJson(Files.readString(configPath, StandardCharsets.UTF_8), JsonObject::class.java)
            val input = root?.get("input")?.asString ?: "placeholder://input"
            val output = root?.get("output")?.asString ?: "placeholder://output"
            input to output
        } catch (_: Exception) {
            "placeholder://input" to "placeholder://output"
        }
    }

    private fun saveConfig(input: String, output: String) {
        try {
            Files.createDirectories(configPath.parent)

            val root = JsonObject()
            root.addProperty("input", if (input.isBlank()) "placeholder://input" else input)
            root.addProperty("output", if (output.isBlank()) "placeholder://output" else output)

            Files.writeString(configPath, gson.toJson(root), StandardCharsets.UTF_8)
            status = Component.literal("Saved to ${configPath.toAbsolutePath()}")
            statusColor = 0x7CDE8A
        } catch (e: Exception) {
            status = Component.literal("Save failed: ${e.message ?: "unknown error"}")
            statusColor = 0xF17A7A
        }
    }
}

