package com.qc.aeonis.block

import com.qc.aeonis.dimension.AncardPortalBlock
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockBehaviour

object AeonisBlocks {
    lateinit var ANCARD_PORTAL: Block
    lateinit var PERMANENT_FLAME: Block
    lateinit var SAFE_CHEST: Block

    fun register() {
        ANCARD_PORTAL = register("ancard_portal") { props ->
            AncardPortalBlock(
                props
                    .noCollision()
                    .strength(-1.0F, 3600000.0F)
                    .lightLevel { 15 }
                    .sound(SoundType.GLASS)
                    .noLootTable()
            )
        }
        PERMANENT_FLAME = register("permanent_flame") { props ->
            PermanentFlameBlock(
                props
                    .noCollision()
                    .strength(-1.0f, 3600000.0f)  // unbreakable by hand
                    .lightLevel { 15 }
                    .sound(SoundType.WOOL)
                    .noLootTable()
                    .noOcclusion()
                    .ignitedByLava()
            )
        }

        SAFE_CHEST = register("safe_chest") { props ->
            SafeChestBlock(
                props
                    .strength(10.0f, 2000.0f) // tough + high blast resistance
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
        }

        registerBlockItem("safe_chest", SAFE_CHEST)
    }

    private fun register(name: String, factory: (BlockBehaviour.Properties) -> Block): Block {
        val id = Identifier.fromNamespaceAndPath("aeonis", name)
        val key = ResourceKey.create(Registries.BLOCK, id)
        val props = BlockBehaviour.Properties.of().setId(key)
        return Registry.register(BuiltInRegistries.BLOCK, id, factory(props))
    }

    private fun registerBlockItem(name: String, block: Block) {
        val id = Identifier.fromNamespaceAndPath("aeonis", name)
        val key = ResourceKey.create(Registries.ITEM, id)
        val itemProps = net.minecraft.world.item.Item.Properties().setId(key)
        Registry.register(BuiltInRegistries.ITEM, id, net.minecraft.world.item.BlockItem(block, itemProps))

        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(
            net.minecraft.world.item.CreativeModeTabs.FUNCTIONAL_BLOCKS
        ).register { entries ->
            entries.accept(block)
        }
    }
}
