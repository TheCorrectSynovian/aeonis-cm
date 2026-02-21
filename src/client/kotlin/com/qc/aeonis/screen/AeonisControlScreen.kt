package com.qc.aeonis.screen

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.SpawnEggItem
import org.lwjgl.glfw.GLFW
import java.util.Locale
import kotlin.math.max

@Environment(EnvType.CLIENT)
class AeonisControlScreen(private val parentScreen: Screen?) : Screen(Component.translatable("options.aeonis.title")) {

    private val allEntityEntries = mutableListOf<EntityEntry>()
    private lateinit var entityList: AeonisEntityListWidget
    private lateinit var searchField: EditBox
    private lateinit var customCommandField: EditBox
    private var selectedEntityType: EntityType<*>? = null
    private var transformButton: Button? = null
    private var rightPaneX = 0
    private var rightPaneWidth = 0
    private var entityListTop = 0
    private var quickCommandsLabelY = 0

    private val quickCommands = listOf(
        QuickCommand("aeonis features extra_mobs true", Component.translatable("options.aeonis.quick.extra_mobs_on")),
        QuickCommand("aeonis features extra_mobs false", Component.translatable("options.aeonis.quick.extra_mobs_off")),
        QuickCommand("aeonis summon_herobrine behind", Component.translatable("options.aeonis.quick.herobrine_behind")),
        QuickCommand("aeonis summon_herobrine roaming", Component.translatable("options.aeonis.quick.herobrine_roaming")),
        QuickCommand("aeonis summon_herobrine staring", Component.translatable("options.aeonis.quick.herobrine_staring")),
        QuickCommand("aeonis summon_herobrine hunting", Component.translatable("options.aeonis.quick.herobrine_hunting")),
        QuickCommand("aeonis reload", Component.translatable("options.aeonis.quick.reload")),
        QuickCommand("aeonis sys ping", Component.translatable("options.aeonis.quick.sys_ping")),
        QuickCommand("ability dash", Component.translatable("options.aeonis.quick.ability_dash")),
        QuickCommand("ability blink 5", Component.translatable("options.aeonis.quick.ability_blink")),
        QuickCommand("ability mimic dragon", Component.translatable("options.aeonis.quick.ability_mimic")),
        QuickCommand("ability summon wolves 3", Component.translatable("options.aeonis.quick.ability_wolves")),
        QuickCommand("event thunder", Component.translatable("options.aeonis.quick.event_thunder")),
        QuickCommand("event ambush", Component.translatable("options.aeonis.quick.event_ambush")),
        QuickCommand("event pro_gamer", Component.translatable("options.aeonis.quick.event_pro_gamer")),
        QuickCommand("event exitbody s", Component.translatable("options.aeonis.quick.event_exitbody"))
    )

    override fun init() {
        super.init()

        entityListTop = 80
        val listHeight = max(height - entityListTop - 140, 180)
        val listLeft = 10
        val listWidth = width / 2 - 16

        searchField = EditBox(font, listLeft, entityListTop - 28, listWidth, 20, Component.translatable("options.aeonis.search"))
        searchField.setMaxLength(64)
        searchField.setValue("")
        searchField.setResponder { rebuildEntityEntries(it) }
        addRenderableWidget(searchField)

        entityList = AeonisEntityListWidget(minecraft, listWidth, listHeight, entityListTop, 32)
        entityList.setX(listLeft)
        entityList.setY(entityListTop)
        addRenderableWidget(entityList)

        populateEntityEntries()
        rebuildEntityEntries("")

        setupRightPane(entityListTop, listHeight)
    }

    private fun populateEntityEntries() {
        allEntityEntries.clear()
        val registry = minecraft.level
            ?.registryAccess()
            ?.lookupOrThrow(Registries.ENTITY_TYPE)
            ?: BuiltInRegistries.ENTITY_TYPE

        for (entityType in registry) {
            allEntityEntries.add(EntityEntry(entityType))
        }

        allEntityEntries.sortBy { it.displayName.string.lowercase(Locale.ROOT) }
    }

    private fun rebuildEntityEntries(filter: String) {
        val normalized = filter.trim().lowercase(Locale.ROOT)
        val matches = if (normalized.isBlank()) {
            allEntityEntries
        } else {
            allEntityEntries.filter { it.matches(normalized) }
        }
        entityList.refreshEntries(matches)
        if (selectedEntityType != null && matches.none { it.entityType == selectedEntityType }) {
            selectedEntityType = null
            transformButton?.active = false
        }
    }

    private fun setupRightPane(listTop: Int, listHeight: Int) {
        rightPaneX = width / 2 + 12
        rightPaneWidth = max(width - rightPaneX - 10, 220)
        var currentY = listTop

        transformButton = Button.builder(Component.translatable("options.aeonis.transform_selected")) { runSelectedTransform() }
            .bounds(rightPaneX, currentY, rightPaneWidth, 20)
            .build()
        transformButton?.active = false
        addRenderableWidget(transformButton!!)
        currentY += 26

        addRenderableWidget(
            Button.builder(Component.translatable("options.aeonis.untransform_button")) { runCommand("untransform") }
                .bounds(rightPaneX, currentY, rightPaneWidth, 20)
                .build()
        )
        currentY += 26

        addRenderableWidget(
            Button.builder(Component.translatable("options.aeonis.quick.soul")) { runCommand("aeonis soul") }
                .bounds(rightPaneX, currentY, rightPaneWidth, 20)
                .build()
        )
        currentY += 26

        addRenderableWidget(
            Button.builder(Component.translatable("options.aeonis.quick.unsoul")) { runCommand("aeonis unsoul") }
                .bounds(rightPaneX, currentY, rightPaneWidth, 20)
                .build()
        )
        currentY += 32

        val customY = listTop + listHeight - 36
        val quickCommandRows = (quickCommands.size + 1) / 2
        val quickButtonHeight = 24
        var quickCommandStartY = currentY
        val quickMaxBottom = customY - 12
        val totalQuickHeight = quickCommandRows * quickButtonHeight
        if (quickCommandStartY + totalQuickHeight > quickMaxBottom) {
            quickCommandStartY = max(currentY, quickMaxBottom - totalQuickHeight)
        }
        quickCommandsLabelY = quickCommandStartY - 18
        val buttonWidth = (rightPaneWidth - 6) / 2

        quickCommands.forEachIndexed { index, quickCommand ->
            val column = index % 2
            val row = index / 2
            val buttonX = rightPaneX + column * (buttonWidth + 6)
            val y = quickCommandStartY + row * (quickButtonHeight + 4)
            addRenderableWidget(
                Button.builder(quickCommand.label) { runCommand(quickCommand.command) }
                    .bounds(buttonX, y, buttonWidth, 20)
                    .build()
            )
        }

        customCommandField = EditBox(font, rightPaneX, customY, rightPaneWidth - 80, 20, Component.translatable("options.aeonis.custom_label"))
        customCommandField.setMaxLength(256)
        addRenderableWidget(customCommandField)

        addRenderableWidget(
            Button.builder(Component.translatable("options.aeonis.custom_button")) { runCustomCommand() }
                .bounds(rightPaneX + rightPaneWidth - 70, customY, 70, 20)
                .build()
        )
    }

    private fun runSelectedTransform() {
        val entity = selectedEntityType ?: return
        val registryKey = BuiltInRegistries.ENTITY_TYPE.getResourceKey(entity).orElse(null) ?: return
        runCommand("transform ${registryKey.identifier()}")
    }

    private fun runCustomCommand() {
        val value = customCommandField.value.trim()
        if (value.isNotEmpty()) {
            runCommand(value)
            customCommandField.setValue("")
        }
    }

    private fun runCommand(command: String) {
        val player = minecraft.player ?: return
        if (command.isBlank()) return
        player.connection.sendCommand(command)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics, mouseX, mouseY, partialTick)
        graphics.drawCenteredString(font, Component.translatable("options.aeonis.title"), width / 2, 14, 0xFFD050)
        graphics.drawString(font, Component.translatable("options.aeonis.list_label"), 10, entityListTop - 26, 0xFFFFFF)
        graphics.drawString(font, Component.translatable("options.aeonis.list_description"), 10, entityListTop - 12, 0xAAAAAA)
        graphics.drawString(font, Component.translatable("options.aeonis.selection_label"), rightPaneX, entityListTop - 26, 0xFFFFFF)
        val nameComponent = selectedEntityType?.let { Component.translatable(it.descriptionId) } ?: Component.translatable("options.aeonis.selected_entity.none")
        graphics.drawString(font, nameComponent, rightPaneX, entityListTop - 12, 0xFFFFFF)
        selectedEntityType?.let { type ->
            BuiltInRegistries.ENTITY_TYPE.getResourceKey(type).ifPresent { id ->
                graphics.drawString(font, Component.literal(id.toString()), rightPaneX, entityListTop, 0x888888)
            }
        }
        graphics.drawString(font, Component.translatable("options.aeonis.quick_actions"), rightPaneX, quickCommandsLabelY, 0xF0F0F0)
        super.render(graphics, mouseX, mouseY, partialTick)
    }

    override fun isPauseScreen(): Boolean = false

    override fun onClose() {
        minecraft.setScreen(parentScreen)
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        if (::searchField.isInitialized && searchField.keyPressed(keyEvent)) {
            return true
        }
        if (::customCommandField.isInitialized && customCommandField.keyPressed(keyEvent)) {
            return true
        }
        if ((keyEvent.key() == GLFW.GLFW_KEY_ENTER || keyEvent.key() == GLFW.GLFW_KEY_KP_ENTER) && ::customCommandField.isInitialized) {
            runCustomCommand()
            return true
        }
        return super.keyPressed(keyEvent)
    }

    override fun charTyped(characterEvent: CharacterEvent): Boolean {
        if (::customCommandField.isInitialized && customCommandField.charTyped(characterEvent)) {
            return true
        }
        if (::searchField.isInitialized && searchField.charTyped(characterEvent)) {
            return true
        }
        return super.charTyped(characterEvent)
    }

    private fun onEntitySelected(entityType: EntityType<*>) {
        selectedEntityType = entityType
        transformButton?.active = true
    }

    private data class QuickCommand(val command: String, val label: Component)

    private inner class AeonisEntityListWidget(
        minecraft: Minecraft,
        width: Int,
        height: Int,
        top: Int,
        entryHeight: Int
    ) : ObjectSelectionList<EntityEntry>(minecraft, width, height, top, entryHeight) {

        fun refreshEntries(entries: Collection<EntityEntry>) {
            super.replaceEntries(entries)
        }

        override fun getRowWidth(): Int = this.width
    }

    private inner class EntityEntry(val entityType: EntityType<*>) : ObjectSelectionList.Entry<EntityEntry>() {
        val displayName: Component = Component.translatable(entityType.descriptionId)
        private val registryKey: ResourceKey<EntityType<*>>? = BuiltInRegistries.ENTITY_TYPE.getResourceKey(entityType).orElse(null)
        private val idText: String = registryKey?.identifier()?.toString() ?: ""
        private val iconStack: ItemStack = ItemStack(SpawnEggItem.byId(entityType) ?: Items.BARRIER)

        fun matches(filter: String): Boolean {
            val display = displayName.string.lowercase(Locale.ROOT)
            return display.contains(filter) || idText.contains(filter)
        }

        override fun renderContent(graphics: GuiGraphics, mouseX: Int, mouseY: Int, hovered: Boolean, tickDelta: Float) {
            val iconX = this.getContentX()
            val iconY = this.getContentY() + (this.getContentHeight() - 16) / 2
            if (this@AeonisControlScreen.minecraft.player != null) {
                graphics.renderItem(iconStack, iconX, iconY)
                graphics.renderItemDecorations(font, iconStack, iconX, iconY)
            }

            val textX = iconX + 24
            graphics.drawString(this@AeonisControlScreen.font, displayName, textX, this.getContentY(), 0xFFFFFF)
            if (idText.isNotEmpty()) {
                graphics.drawString(this@AeonisControlScreen.font, Component.literal(idText), textX, this.getContentY() + 11, 0x777777)
            }
        }

        override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, bl: Boolean): Boolean {
            this@AeonisControlScreen.entityList.setSelected(this)
            this@AeonisControlScreen.onEntitySelected(entityType)
            return true
        }

        override fun getNarration(): Component = displayName
    }
}
