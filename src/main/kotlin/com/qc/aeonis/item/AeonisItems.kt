package com.qc.aeonis.item

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.Item
import net.minecraft.core.Registry

object AeonisItems {
    lateinit var SOUL: Item

    fun register() {
        SOUL = register("soul") { Item(it) }

        // Add items to a creative tab
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(net.minecraft.world.item.CreativeModeTabs.TOOLS_AND_UTILITIES).register { entries ->
            entries.accept(SOUL)
        }
    }

    private fun register(name: String, factory: (Item.Properties) -> Item): Item {
        val id = ResourceLocation.fromNamespaceAndPath("aeonis", name)
        val key = ResourceKey.create(Registries.ITEM, id)
        val props = Item.Properties().setId(key)
        return Registry.register(BuiltInRegistries.ITEM, id, factory(props))
    }
}
