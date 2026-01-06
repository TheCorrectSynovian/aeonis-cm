package com.qc.aeonis.item

import com.qc.aeonis.entity.AeonisEntities
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.SpawnEggItem
import net.minecraft.core.Registry

object AeonisItems {
    lateinit var SOUL: Item
    lateinit var HEROBRINE_SPAWN_EGG: Item
    lateinit var COPPER_STALKER_SPAWN_EGG: Item

    fun register() {
        SOUL = register("soul") { Item(it) }
        
        // Herobrine spawn egg - uses custom texture for colors
        HEROBRINE_SPAWN_EGG = register("herobrine_spawn_egg") { props ->
            SpawnEggItem(props.spawnEgg(AeonisEntities.HEROBRINE))
        }
        
        // Copper Stalker spawn egg - copper/orange themed
        COPPER_STALKER_SPAWN_EGG = register("copper_stalker_spawn_egg") { props ->
            SpawnEggItem(props.spawnEgg(AeonisEntities.COPPER_STALKER))
        }

        // Add items to a creative tab
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(net.minecraft.world.item.CreativeModeTabs.TOOLS_AND_UTILITIES).register { entries ->
            entries.accept(SOUL)
        }
        
        // Add spawn egg to spawn eggs tab
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(net.minecraft.world.item.CreativeModeTabs.SPAWN_EGGS).register { entries ->
            entries.accept(HEROBRINE_SPAWN_EGG)
            entries.accept(COPPER_STALKER_SPAWN_EGG)
        }
    }

    private fun register(name: String, factory: (Item.Properties) -> Item): Item {
        val id = Identifier.fromNamespaceAndPath("aeonis", name)
        val key = ResourceKey.create(Registries.ITEM, id)
        val props = Item.Properties().setId(key)
        return Registry.register(BuiltInRegistries.ITEM, id, factory(props))
    }
}
