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
import net.minecraft.world.level.material.MapColor

/**
 * All custom blocks for the Ancard dimension.
 * Replaces overworld stone/dirt/ores with unique Ancard variants.
 */
object AncardBlocks {
    // --- Terrain Blocks ---
    lateinit var ANCARD_STONE: Block
    lateinit var CHARRED_GRAVEL: Block
    lateinit var OBSIDIAN_SHALE: Block
    lateinit var ANCARD_BASALT: Block
    lateinit var ANCARD_DEEPSLITE: Block

    // --- Biome Vegetation ---
    lateinit var BLOODROOT_GRASS: Block
    lateinit var BLOODROOT_LOG: Block
    lateinit var BLOODROOT_LEAVES: Block
    lateinit var BLOODROOT_PLANKS: Block
    lateinit var BLOODROOT_MUSHROOM: Block
    lateinit var BLOODROOT_VINE: Block

    lateinit var VEILSHADE_NYLIUM: Block
    lateinit var VEILSHADE_FUNGUS: Block
    lateinit var VEILSHADE_SPORE_BLOCK: Block

    // --- Ores ---
    lateinit var ANCARD_SOUL_ORE: Block
    lateinit var ANCARD_CRIMSON_ORE: Block
    lateinit var ANCARD_VOID_ORE: Block

    // --- Structure Blocks ---
    lateinit var DARK_OBSIDIAN: Block
    lateinit var RUINED_OBSIDIAN_BRICKS: Block
    lateinit var ANCARD_SPAWNER_CAGE: Block

    fun register() {
        // Terrain
        ANCARD_STONE = register("ancard_stone") { props ->
            Block(props.mapColor(MapColor.COLOR_BLACK)
                .strength(2.0f, 8.0f)
                .requiresCorrectToolForDrops()
                .sound(SoundType.DEEPSLATE))
        }

        CHARRED_GRAVEL = register("charred_gravel") { props ->
            Block(props.mapColor(MapColor.COLOR_GRAY)
                .strength(0.8f)
                .sound(SoundType.GRAVEL))
        }

        OBSIDIAN_SHALE = register("obsidian_shale") { props ->
            Block(props.mapColor(MapColor.COLOR_BLACK)
                .strength(35.0f, 1200.0f)
                .requiresCorrectToolForDrops()
                .sound(SoundType.GILDED_BLACKSTONE))
        }

        ANCARD_BASALT = register("ancard_basalt") { props ->
            Block(props.mapColor(MapColor.COLOR_BLACK)
                .strength(2.5f, 7.0f)
                .requiresCorrectToolForDrops()
                .sound(SoundType.BASALT))
        }

        ANCARD_DEEPSLITE = register("ancard_deepslate") { props ->
            Block(props.mapColor(MapColor.DEEPSLATE)
                .strength(3.5f, 9.0f)
                .requiresCorrectToolForDrops()
                .sound(SoundType.DEEPSLATE))
        }

        // Bloodroot Expanse vegetation
        BLOODROOT_GRASS = register("bloodroot_grass") { props ->
            Block(props.mapColor(MapColor.COLOR_RED)
                .strength(0.6f)
                .sound(SoundType.NYLIUM)
                .lightLevel { 3 })
        }

        BLOODROOT_LOG = register("bloodroot_log") { props ->
            Block(props.mapColor(MapColor.CRIMSON_NYLIUM)
                .strength(2.0f)
                .sound(SoundType.STEM))
        }

        BLOODROOT_LEAVES = register("bloodroot_leaves") { props ->
            Block(props.mapColor(MapColor.COLOR_RED)
                .strength(0.2f)
                .sound(SoundType.AZALEA_LEAVES)
                .noOcclusion()
                .lightLevel { 5 })
        }

        BLOODROOT_PLANKS = register("bloodroot_planks") { props ->
            Block(props.mapColor(MapColor.CRIMSON_STEM)
                .strength(2.0f, 3.0f)
                .sound(SoundType.NETHER_WOOD))
        }

        BLOODROOT_MUSHROOM = register("bloodroot_mushroom") { props ->
            Block(props.mapColor(MapColor.COLOR_RED)
                .noCollision()
                .instabreak()
                .sound(SoundType.FUNGUS)
                .lightLevel { 8 })
        }

        BLOODROOT_VINE = register("bloodroot_vine") { props ->
            Block(props.mapColor(MapColor.COLOR_RED)
                .noCollision()
                .instabreak()
                .sound(SoundType.VINE)
                .lightLevel { 2 })
        }

        // Veilshade Hollow vegetation
        VEILSHADE_NYLIUM = register("veilshade_nylium") { props ->
            Block(props.mapColor(MapColor.WARPED_NYLIUM)
                .strength(0.6f)
                .sound(SoundType.NYLIUM)
                .lightLevel { 4 })
        }

        VEILSHADE_FUNGUS = register("veilshade_fungus") { props ->
            Block(props.mapColor(MapColor.COLOR_CYAN)
                .noCollision()
                .instabreak()
                .sound(SoundType.FUNGUS)
                .lightLevel { 10 })
        }

        VEILSHADE_SPORE_BLOCK = register("veilshade_spore_block") { props ->
            Block(props.mapColor(MapColor.WARPED_WART_BLOCK)
                .strength(1.0f)
                .sound(SoundType.WART_BLOCK)
                .lightLevel { 6 })
        }

        // Ores
        ANCARD_SOUL_ORE = register("ancard_soul_ore") { props ->
            Block(props.mapColor(MapColor.COLOR_LIGHT_BLUE)
                .strength(3.0f, 5.0f)
                .requiresCorrectToolForDrops()
                .sound(SoundType.DEEPSLATE)
                .lightLevel { 4 })
        }

        ANCARD_CRIMSON_ORE = register("ancard_crimson_ore") { props ->
            Block(props.mapColor(MapColor.COLOR_RED)
                .strength(4.0f, 6.0f)
                .requiresCorrectToolForDrops()
                .sound(SoundType.DEEPSLATE)
                .lightLevel { 3 })
        }

        ANCARD_VOID_ORE = register("ancard_void_ore") { props ->
            Block(props.mapColor(MapColor.COLOR_PURPLE)
                .strength(5.0f, 8.0f)
                .requiresCorrectToolForDrops()
                .sound(SoundType.DEEPSLATE)
                .lightLevel { 2 })
        }

        // Structure blocks
        DARK_OBSIDIAN = register("dark_obsidian") { props ->
            Block(props.mapColor(MapColor.COLOR_BLACK)
                .strength(50.0f, 1200.0f)
                .requiresCorrectToolForDrops()
                .sound(SoundType.STONE)
                .lightLevel { 1 })
        }

        RUINED_OBSIDIAN_BRICKS = register("ruined_obsidian_bricks") { props ->
            Block(props.mapColor(MapColor.COLOR_BLACK)
                .strength(40.0f, 1000.0f)
                .requiresCorrectToolForDrops()
                .sound(SoundType.GILDED_BLACKSTONE))
        }

        ANCARD_SPAWNER_CAGE = register("ancard_spawner_cage") { props ->
            Block(props.mapColor(MapColor.COLOR_BLACK)
                .strength(5.0f, 6.0f)
                .sound(SoundType.METAL)
                .noOcclusion()
                .lightLevel { 5 })
        }

        // Register block items for all blocks
        registerBlockItems()
    }

    private fun registerBlockItems() {
        val blocks = listOf(
            "ancard_stone" to ANCARD_STONE,
            "charred_gravel" to CHARRED_GRAVEL,
            "obsidian_shale" to OBSIDIAN_SHALE,
            "ancard_basalt" to ANCARD_BASALT,
            "ancard_deepslate" to ANCARD_DEEPSLITE,
            "bloodroot_grass" to BLOODROOT_GRASS,
            "bloodroot_log" to BLOODROOT_LOG,
            "bloodroot_leaves" to BLOODROOT_LEAVES,
            "bloodroot_planks" to BLOODROOT_PLANKS,
            "bloodroot_mushroom" to BLOODROOT_MUSHROOM,
            "bloodroot_vine" to BLOODROOT_VINE,
            "veilshade_nylium" to VEILSHADE_NYLIUM,
            "veilshade_fungus" to VEILSHADE_FUNGUS,
            "veilshade_spore_block" to VEILSHADE_SPORE_BLOCK,
            "ancard_soul_ore" to ANCARD_SOUL_ORE,
            "ancard_crimson_ore" to ANCARD_CRIMSON_ORE,
            "ancard_void_ore" to ANCARD_VOID_ORE,
            "dark_obsidian" to DARK_OBSIDIAN,
            "ruined_obsidian_bricks" to RUINED_OBSIDIAN_BRICKS,
            "ancard_spawner_cage" to ANCARD_SPAWNER_CAGE
        )

        for ((name, block) in blocks) {
            val id = Identifier.fromNamespaceAndPath("aeonis", name)
            val key = ResourceKey.create(Registries.ITEM, id)
            val itemProps = net.minecraft.world.item.Item.Properties().setId(key)
            Registry.register(
                BuiltInRegistries.ITEM,
                id,
                net.minecraft.world.item.BlockItem(block, itemProps)
            )
        }

        // Add to creative tab
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(
            net.minecraft.world.item.CreativeModeTabs.BUILDING_BLOCKS
        ).register { entries ->
            for ((_, block) in blocks) {
                entries.accept(block)
            }
        }
    }

    private fun register(name: String, factory: (BlockBehaviour.Properties) -> Block): Block {
        val id = Identifier.fromNamespaceAndPath("aeonis", name)
        val key = ResourceKey.create(Registries.BLOCK, id)
        val props = BlockBehaviour.Properties.of().setId(key)
        return Registry.register(BuiltInRegistries.BLOCK, id, factory(props))
    }
}
