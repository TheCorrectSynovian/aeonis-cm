package com.qc.aeonis.dimension

import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.WrittenBookContent

/**
 * Ancient lore fragments found in Ancard structures.
 * These are written books containing fragments of the dimension's history.
 */
object AncardLoreFragments {

    private val LORE_ENTRIES = listOf(
        LoreEntry(
            "Fragment of Ash",
            "Unknown Scholar",
            listOf(
                "The Ash Barrens were not always as they are now. Once, great volcanoes pierced the ceiling of Ancard, their fires lighting the eternal dark.",
                "When the Sovereign rose, the volcanoes fell silent. Now only ash remains — an ocean of gray where nothing grows, where the Stalkers burrow.",
                "The Stalkers remember the fire. They wear its immunity as armor. When the world burned, they were born."
            )
        ),
        LoreEntry(
            "Bloodroot Codex",
            "Keeper Vaelith",
            listOf(
                "The Bloodroot trees... they are not trees at all. They are veins of the dimension itself, pumping a dark ichor through the stone.",
                "Those who drink of the sap become Fiends — twisted, fast, hungry. Their bleed is the dimension's heartbeat, shared through violence.",
                "I record this knowing I too have tasted the root. My hands shake. My vision reddens. The Expanse calls me home."
            )
        ),
        LoreEntry(
            "Veilshade Whispers",
            "Dimensional Archivist",
            listOf(
                "The Hollow is the oldest place in Ancard. The Watchers have observed since before the Sovereign. They do not attack — not first.",
                "The spores carry memory. Breathe deep and you may hear fragments of a world that existed before the veil descended.",
                "Those who harm a Watcher find no mercy. They teleport, they strike, they vanish. Patience is their trap."
            )
        ),
        LoreEntry(
            "The Sovereign's Rise",
            "Scribe Moraleth",
            listOf(
                "From the depths of the Citadel, IT rose. Not born, not made — manifested from the collective darkness of Ancard.",
                "Three phases of its wrath I witnessed. First: calculated strikes. Second: the summoning of its children. Third: the storm.",
                "None have slain the Sovereign. Those who tried... their echoes still circle the Citadel arena, trapped in obsidian memory.",
                "Yet the lore speaks of a weapon forged from all three biomes — ash, blood, and veil. Perhaps that is merely hope."
            )
        ),
        LoreEntry(
            "On the Coordinate Scale",
            "Navigator Crenth",
            listOf(
                "Eight steps in the Overworld equal one in Ancard. The dimension is compressed, folded upon itself.",
                "Those who build portals must beware — a short walk in Ancard spans vast distances beyond.",
                "The Nether discovered this truth eons ago. Ancard learned it from the Nether's bones."
            )
        )
    )

    /**
     * Get a random lore fragment as a written book ItemStack.
     */
    fun getRandomLoreBook(): ItemStack {
        val lore = LORE_ENTRIES.random()
        return createLoreBook(lore)
    }

    /**
     * Get a specific lore fragment by index.
     */
    fun getLoreBook(index: Int): ItemStack {
        val lore = LORE_ENTRIES[index.coerceIn(0, LORE_ENTRIES.size - 1)]
        return createLoreBook(lore)
    }

    private fun createLoreBook(lore: LoreEntry): ItemStack {
        val stack = ItemStack(Items.WRITTEN_BOOK)
        // In 1.21+ books use components
        // The actual written book content would be set via component system
        // For now, create the item and set display name
        stack.set(
            net.minecraft.core.component.DataComponents.CUSTOM_NAME,
            Component.literal("§4§l${lore.title}")
        )
        return stack
    }

    data class LoreEntry(
        val title: String,
        val author: String,
        val pages: List<String>
    )
}
