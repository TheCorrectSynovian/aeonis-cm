package com.qc.aeonis.screen

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.util.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.Identifier
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

    private val logoTexture = Identifier.fromNamespaceAndPath("aeonis-manager", "icon.png")
    private val modId = "aeonis-manager"
    private val modName = "Aeonis Command Master"
    private val modVersion: String = FabricLoader.getInstance()
        .getModContainer(modId)
        .map { it.metadata.version.friendlyString }
        .orElse("3.1.0")

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

        entityListTop = 94
        val listHeight = max(height - entityListTop - 120, 190)
        val listLeft = 10
        val listWidth = width / 2 - 18

        searchField = EditBox(font, listLeft, entityListTop - 28, listWidth, 20, Component.translatable("options.aeonis.search"))
        searchField.setMaxLength(64)
        searchField.setValue("")
        searchField.setResponder { rebuildEntityEntries(it) }
        searchField.setHint(Component.literal("Search entity id or name..."))
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
        rightPaneWidth = max(width - rightPaneX - 10, 230)
        var currentY = listTop

        transformButton = Button.builder(Component.literal("Transform Selected")) { runSelectedTransform() }
            .bounds(rightPaneX, currentY, rightPaneWidth, 22)
            .build()
        transformButton?.active = false
        addRenderableWidget(transformButton!!)
        currentY += 28

        addRenderableWidget(
            Button.builder(Component.literal("Untransform")) { runCommand("untransform") }
                .bounds(rightPaneX, currentY, rightPaneWidth, 22)
                .build()
        )
        currentY += 28

        addRenderableWidget(
            Button.builder(Component.literal("Soul Mode")) { runCommand("aeonis soul") }
                .bounds(rightPaneX, currentY, rightPaneWidth, 22)
                .build()
        )
        currentY += 28

        addRenderableWidget(
            Button.builder(Component.literal("Unsoul")) { runCommand("aeonis unsoul") }
                .bounds(rightPaneX, currentY, rightPaneWidth, 22)
                .build()
        )
        currentY += 32

        val customY = listTop + listHeight - 30
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
                    .bounds(buttonX, y, buttonWidth, 21)
                    .build()
            )
        }

        customCommandField = EditBox(font, rightPaneX, customY, rightPaneWidth - 80, 21, Component.translatable("options.aeonis.custom_label"))
        customCommandField.setMaxLength(256)
        customCommandField.setHint(Component.literal("Type any command..."))
        addRenderableWidget(customCommandField)

        addRenderableWidget(
            Button.builder(Component.translatable("options.aeonis.custom_button")) { runCustomCommand() }
                .bounds(rightPaneX + rightPaneWidth - 70, customY, 70, 21)
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
        drawModernBackground(graphics)
        drawHeader(graphics)
        drawPanels(graphics)

        graphics.drawString(font, Component.literal("Entity Registry"), 12, entityListTop - 26, 0xE8F0FF)
        graphics.drawString(font, Component.literal("Select a creature to transform into"), 12, entityListTop - 12, 0x9FAEC9)
        graphics.drawString(font, Component.literal("Selection"), rightPaneX, entityListTop - 26, 0xE8F0FF)
        val nameComponent = selectedEntityType?.let { Component.translatable(it.descriptionId) } ?: Component.translatable("options.aeonis.selected_entity.none")
        graphics.drawString(font, nameComponent, rightPaneX, entityListTop - 12, 0xFFFFFF)
        selectedEntityType?.let { type ->
            BuiltInRegistries.ENTITY_TYPE.getResourceKey(type).ifPresent { id ->
                graphics.drawString(font, Component.literal(id.toString()), rightPaneX, entityListTop, 0x7E8CAB)
            }
        }
        graphics.drawString(font, Component.literal("Quick Actions"), rightPaneX, quickCommandsLabelY, 0xD8E2FA)
        graphics.drawString(font, Component.literal("Custom"), rightPaneX, customCommandField.y - 12, 0xD8E2FA)

        drawSelectionCard(graphics)

        super.render(graphics, mouseX, mouseY, partialTick)
    }

    private fun drawModernBackground(graphics: GuiGraphics) {
        val pulse = ((Util.getMillis() / 20L) % 40L).toInt()
        val drift = pulse - 20
        val topStart = 0xE5131826.toInt()
        val topEnd = 0xEA09121F.toInt()
        val bottomStart = 0xF0112943.toInt()
        val bottomEnd = 0xF0061A2E.toInt()

        graphics.fillGradient(0, 0, width, height / 2 + drift, topStart, topEnd)
        graphics.fillGradient(0, height / 3, width, height, bottomStart, bottomEnd)
    }

    private fun drawHeader(graphics: GuiGraphics) {
        val headerTop = 8
        val headerLeft = 8
        val headerRight = width - 8
        val headerHeight = 60
        val headerBottomLocal = headerTop + headerHeight

        graphics.fill(headerLeft, headerTop, headerRight, headerBottomLocal, 0xAA101C30.toInt())
        graphics.hLine(headerLeft, headerRight, headerTop, 0xFF4AB8FF.toInt())
        graphics.hLine(headerLeft, headerRight, headerBottomLocal, 0xFF263A57.toInt())
        graphics.vLine(headerLeft, headerTop, headerBottomLocal, 0xFF2F4968.toInt())
        graphics.vLine(headerRight, headerTop, headerBottomLocal, 0xFF2F4968.toInt())

        val logoSize = 40
        val logoX = headerLeft + 10
        val logoY = headerTop + 10
        graphics.blit(logoTexture, logoX, logoX + logoSize, logoY, logoY + logoSize, 0f, 1f, 0f, 1f)

        val titleX = logoX + logoSize + 10
        graphics.drawString(font, Component.literal("AEONIS COMMAND CONSOLE"), titleX, headerTop + 14, 0xF4F8FF, false)
        graphics.drawString(font, Component.literal("Modern runtime control panel"), titleX, headerTop + 28, 0x9EC1EC, false)
        graphics.drawString(font, Component.literal("Mod Name: $modName"), headerRight - 220, headerTop + 12, 0xC6D9F7, false)
        graphics.drawString(font, Component.literal("Mod ID: $modId"), headerRight - 220, headerTop + 24, 0x88A7D0, false)
        graphics.drawString(font, Component.literal("Version: $modVersion"), headerRight - 220, headerTop + 36, 0x88A7D0, false)
    }

    private fun drawPanels(graphics: GuiGraphics) {
        val left = 8
        val rightSplit = width / 2
        val top = entityListTop - 34
        val bottom = height - 14

        graphics.fill(left, top, rightSplit - 8, bottom, 0x8A0E1B2F.toInt())
        graphics.fill(rightSplit + 4, top, width - 8, bottom, 0x8A0C1A2D.toInt())

        graphics.hLine(left, rightSplit - 8, top, 0xFF2B4E73.toInt())
        graphics.hLine(rightSplit + 4, width - 8, top, 0xFF2B4E73.toInt())
        graphics.hLine(left, rightSplit - 8, bottom, 0xFF1E324A.toInt())
        graphics.hLine(rightSplit + 4, width - 8, bottom, 0xFF1E324A.toInt())
    }

    private fun drawSelectionCard(graphics: GuiGraphics) {
        val cardX = rightPaneX
        val cardY = entityListTop + 18
        val cardW = rightPaneWidth
        val cardH = 56
        graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xAE132642.toInt())
        graphics.hLine(cardX, cardX + cardW, cardY, 0xFF4BA8FF.toInt())
        graphics.hLine(cardX, cardX + cardW, cardY + cardH, 0xFF253E5B.toInt())

        val selected = selectedEntityType
        if (selected == null) {
            graphics.drawString(font, Component.literal("No entity selected"), cardX + 10, cardY + 10, 0xC2D2EC, false)
            graphics.drawString(font, Component.literal("Pick one from the registry list"), cardX + 10, cardY + 24, 0x7E93B6, false)
            return
        }

        val iconStack = ItemStack(SpawnEggItem.byId(selected) ?: Items.BARRIER)
        graphics.renderItem(iconStack, cardX + 8, cardY + 8)
        graphics.renderItemDecorations(font, iconStack, cardX + 8, cardY + 8)
        graphics.drawString(font, Component.translatable(selected.descriptionId), cardX + 30, cardY + 10, 0xFFFFFF, false)
        val idText = BuiltInRegistries.ENTITY_TYPE.getResourceKey(selected)
            .map { it.identifier().toString() }
            .orElse("unknown")
        graphics.drawString(font, Component.literal(idText), cardX + 30, cardY + 24, 0x88A8D4, false)
        graphics.drawString(font, Component.literal("One-click transform ready"), cardX + 30, cardY + 38, 0x78C2FF, false)
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
