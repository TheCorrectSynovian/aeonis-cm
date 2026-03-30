package com.qc.aeonis.block

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockBehaviour

object AeonisBlocks {
    lateinit var ANCARD_PORTAL: Block
    lateinit var PERMANENT_FLAME: Block
    lateinit var SAFE_CHEST: Block
    lateinit var TUNGSTEN_ORE: Block
    lateinit var DEEPSLATE_TUNGSTEN_ORE: Block
    lateinit var TUNGSTEN_BLOCK: Block
    lateinit var ANDESITE_STONE: Block
    lateinit var LEAF_STAND: Block
    lateinit var LIMESTONE_CRYSTAL: Block
    lateinit var SANDSTONE_POLISHED: Block
    lateinit var TRAVERTINE: Block
    lateinit var TRUMPED_CONCREATE: Block
    lateinit var TRUMPED_WALL: Block
    lateinit var VOLCANIC_STONE: Block

    fun register() {
        // Disabled: Ancard content is intentionally not registered at runtime.
        ANCARD_PORTAL = Blocks.NETHER_PORTAL
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

        TUNGSTEN_ORE = register("tungsten_ore") { props ->
            Block(
                props
                    .strength(3.0f, 3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
            )
        }

        DEEPSLATE_TUNGSTEN_ORE = register("deepslate_tungsten_ore") { props ->
            Block(
                props
                    .strength(4.5f, 3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE)
            )
        }

        TUNGSTEN_BLOCK = register("tungsten_block") { props ->
            Block(
                props
                    .strength(5.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            )
        }

        ANDESITE_STONE = register("andesite_stone") { props ->
            Block(
                props
                    .strength(1.6f, 6.0f)
                    .sound(SoundType.STONE)
            )
        }

        LEAF_STAND = register("leaf_stand") { props ->
            Block(
                props
                    .strength(0.8f, 1.0f)
                    .sound(SoundType.AZALEA)
            )
        }

        LIMESTONE_CRYSTAL = register("limestone_crystal") { props ->
            Block(
                props
                    .strength(2.2f, 4.0f)
                    .sound(SoundType.CALCITE)
            )
        }

        SANDSTONE_POLISHED = register("sandstone_polished") { props ->
            Block(
                props
                    .strength(1.2f, 3.0f)
                    .sound(SoundType.STONE)
            )
        }

        TRAVERTINE = register("travertine") { props ->
            Block(
                props
                    .strength(1.5f, 4.0f)
                    .sound(SoundType.CALCITE)
            )
        }

        TRUMPED_CONCREATE = register("trumped_concreate") { props ->
            Block(
                props
                    .strength(1.8f, 6.0f)
                    .sound(SoundType.STONE)
            )
        }

        TRUMPED_WALL = register("trumped_wall") { props ->
            Block(
                props
                    .strength(1.8f, 6.0f)
                    .sound(SoundType.STONE)
            )
        }

        VOLCANIC_STONE = register("volcanic_stone") { props ->
            Block(
                props
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.BASALT)
            )
        }

        registerBlockItem("safe_chest", SAFE_CHEST)
        registerBlockItem("permanent_flame", PERMANENT_FLAME)
        registerBlockItem("tungsten_ore", TUNGSTEN_ORE)
        registerBlockItem("deepslate_tungsten_ore", DEEPSLATE_TUNGSTEN_ORE)
        registerBlockItem("tungsten_block", TUNGSTEN_BLOCK)
        registerBlockItem("andesite_stone", ANDESITE_STONE)
        registerBlockItem("leaf_stand", LEAF_STAND)
        registerBlockItem("limestone_crystal", LIMESTONE_CRYSTAL)
        registerBlockItem("sandstone_polished", SANDSTONE_POLISHED)
        registerBlockItem("travertine", TRAVERTINE)
        registerBlockItem("trumped_concreate", TRUMPED_CONCREATE)
        registerBlockItem("trumped_wall", TRUMPED_WALL)
        registerBlockItem("volcanic_stone", VOLCANIC_STONE)
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
    }

}
