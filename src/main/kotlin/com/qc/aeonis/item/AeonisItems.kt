package com.qc.aeonis.item

import com.qc.aeonis.entity.AeonisEntities
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.food.FoodProperties
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.item.AxeItem
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.DoubleHighBlockItem
import net.minecraft.world.item.HoeItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.SpawnEggItem
import net.minecraft.world.item.ShovelItem
import net.minecraft.world.item.ToolMaterial
import net.minecraft.world.item.equipment.ArmorMaterial
import net.minecraft.world.item.equipment.ArmorType
import net.minecraft.world.item.equipment.EquipmentAssets
import net.minecraft.tags.ItemTags
import net.minecraft.sounds.SoundEvents
import net.minecraft.core.Registry

object AeonisItems {
    private val TUNGSTEN_TOOL_MATERIAL_TAG = TagKey.create(
        Registries.ITEM,
        Identifier.fromNamespaceAndPath("aeonis", "tungsten_tool_materials")
    )

    private val TUNGSTEN_TOOL_MATERIAL = ToolMaterial(
        BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
        900,
        7.0f,
        2.5f,
        16,
        TUNGSTEN_TOOL_MATERIAL_TAG
    )

    private val SCULKERITE_ARMOR_MATERIAL = ArmorMaterial(
        37,
        mapOf(
            ArmorType.BOOTS to 3,
            ArmorType.LEGGINGS to 6,
            ArmorType.CHESTPLATE to 8,
            ArmorType.HELMET to 3,
            ArmorType.BODY to 11
        ),
        18,
        SoundEvents.ARMOR_EQUIP_NETHERITE,
        3.0f,
        0.1f,
        ItemTags.REPAIRS_NETHERITE_ARMOR,
        ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath("aeonis", "sculkerite"))
    )

    private val WARDEN_ARMOR_MATERIAL = ArmorMaterial(
        40,
        mapOf(
            ArmorType.BOOTS to 4,
            ArmorType.LEGGINGS to 7,
            ArmorType.CHESTPLATE to 9,
            ArmorType.HELMET to 4,
            ArmorType.BODY to 12
        ),
        15,
        SoundEvents.ARMOR_EQUIP_NETHERITE,
        3.5f,
        0.15f,
        ItemTags.REPAIRS_NETHERITE_ARMOR,
        ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath("aeonis", "warden"))
    )

    lateinit var SOUL: Item
    lateinit var HEROBRINE_SPAWN_EGG: Item
    lateinit var COPPER_STALKER_SPAWN_EGG: Item
    lateinit var ANCARD_LIGHTER: Item

    // Ancard dimension spawn eggs
    lateinit var ASH_STALKER_SPAWN_EGG: Item
    lateinit var BLOODROOT_FIEND_SPAWN_EGG: Item
    lateinit var VEILSHADE_WATCHER_SPAWN_EGG: Item
    lateinit var ANCARD_SOVEREIGN_SPAWN_EGG: Item
    lateinit var SHADE_LURKER_SPAWN_EGG: Item
    lateinit var OBELISK_SENTINEL_SPAWN_EGG: Item
    lateinit var CRYPT_MITE_SPAWN_EGG: Item
    lateinit var BONEWEAVER_SPAWN_EGG: Item
    lateinit var ECHO_WISP_SPAWN_EGG: Item
    lateinit var RUIN_HOUND_SPAWN_EGG: Item
    lateinit var VEIL_MIMIC_SPAWN_EGG: Item
    lateinit var SPOREBACK_SPAWN_EGG: Item
    lateinit var RIFT_SCREECHER_SPAWN_EGG: Item
    lateinit var ANCIENT_COLOSSUS_SPAWN_EGG: Item
    lateinit var HUNTER_SPAWN_EGG: Item

    // Crimson materials
    lateinit var RAW_CRIMSON: Item
    lateinit var CRIMSON_INGOT_SCRAPS: Item
    lateinit var TUNGSTEN_RAW: Item
    lateinit var TUNGSTEN_INGOT: Item
    lateinit var TUNGSTEN_SWORD: Item
    lateinit var TUNGSTEN_PICKAXE: Item
    lateinit var TUNGSTEN_AXE: Item
    lateinit var TUNGSTEN_SHOVEL: Item
    lateinit var TUNGSTEN_HOE: Item

    // Companion whistle
    lateinit var RHISTEL: Item

    // Arda Sculk spawn eggs
    lateinit var RADIOACTIVE_WARDEN_SPAWN_EGG: Item
    lateinit var SCULK_BOSS_1_SPAWN_EGG: Item
    lateinit var SCULK_CREAKING_SPAWN_EGG: Item
    lateinit var SCULK_CREEPER_ANIMATION_SPAWN_EGG: Item
    lateinit var SCULK_ENDERMAN_SPAWN_EGG: Item
    lateinit var SCULK_GOLEM_BOSS_SPAWN_EGG: Item
    lateinit var SCULK_SKELETON_SPAWN_EGG: Item
    lateinit var SCULK_SLIME_SPAWN_EGG: Item
    lateinit var SHADOW_HUNTER_SPAWN_EGG: Item

    // ═══════════════════════════════════════════════════════════════════
    // SCULK MATERIALS & INGREDIENTS
    // ═══════════════════════════════════════════════════════════════════
    lateinit var SCULK_PIECE: Item
    lateinit var SCULK_INGOT: Item
    lateinit var SCULK_BONE: Item
    lateinit var SCULK_CRYSTAL: Item
    lateinit var SCULK_SENSOR_PART: Item
    lateinit var SCULK_RESIN_BRICK: Item
    lateinit var SCULK_RESIN_CLUMP: Item
    lateinit var SCULKERITE_NUGGET: Item
    lateinit var SCULKED_EMERALD: Item
    lateinit var GOLDEN_SCULK_PIECE: Item
    lateinit var GOLDEN_SCULK_SENSOR_PART: Item
    lateinit var GOLDEN_WARDEN_CLAW: Item
    lateinit var RADIOACTIVE_SCULK_PIECE: Item
    lateinit var RADIOACTIVE_SCULK_SENSOR_PART: Item
    lateinit var RADIOACTIVE_WARDEN_CLAW: Item
    lateinit var WARDEN_CLAW: Item
    lateinit var SACRED_OBSIDIAN_SHARD: Item
    lateinit var ROTTEN_CREAKING_SHELL: Item
    lateinit var ANCIENT_NAUTILUS_SHELL: Item
    lateinit var AGILE_SLIME_BALL: Item
    lateinit var DEEP_DARK_UPGRADE_TEMPLATE: Item
    lateinit var SCULKERITE_UPGRADE_SMITHING_TEMPLATE: Item

    // ═══════════════════════════════════════════════════════════════════
    // STICKS
    // ═══════════════════════════════════════════════════════════════════
    lateinit var BLACKBIRCH_STICK: Item
    lateinit var RADIOACTIVE_BALSA_STICK: Item
    lateinit var SCULK_PLANKS_STICK: Item
    lateinit var WISTERYA_STICK: Item

    // ═══════════════════════════════════════════════════════════════════
    // WOODEN TOOLS — Black Birch
    // ═══════════════════════════════════════════════════════════════════
    lateinit var BLACKBIRCH_WOODEN_AXE: Item
    lateinit var BLACKBIRCH_WOODEN_HOE: Item
    lateinit var BLACKBIRCH_WOODEN_PICKAXE: Item
    lateinit var BLACKBIRCH_WOODEN_SHOVEL: Item
    lateinit var BLACKBIRCH_WOODEN_SWORD: Item

    // ═══════════════════════════════════════════════════════════════════
    // WOODEN TOOLS — Sculk
    // ═══════════════════════════════════════════════════════════════════
    lateinit var SCULK_WOODEN_AXE: Item
    lateinit var SCULK_WOODEN_HOE: Item
    lateinit var SCULK_WOODEN_PICKAXE: Item
    lateinit var SCULK_WOODEN_SHOVEL: Item
    lateinit var SCULK_WOODEN_SWORD: Item

    // ═══════════════════════════════════════════════════════════════════
    // WOODEN TOOLS — Wisterya
    // ═══════════════════════════════════════════════════════════════════
    lateinit var WISTERYA_WOODEN_AXE: Item
    lateinit var WISTERYA_WOODEN_HOE: Item
    lateinit var WISTERYA_WOODEN_PICKAXE: Item
    lateinit var WISTERYA_WOODEN_SHOVEL: Item
    lateinit var WISTERYA_WOODEN_SWORD: Item

    // ═══════════════════════════════════════════════════════════════════
    // WOODEN TOOLS — Radioactive Balsa
    // ═══════════════════════════════════════════════════════════════════
    lateinit var RADIOACTIVE_PLANKS_AXE: Item
    lateinit var RADIOACTIVE_PLANKS_HOE: Item
    lateinit var RADIOACTIVE_PLANKS_PICKAXE: Item
    lateinit var RADIOACTIVE_PLANKS_SHOVEL: Item
    lateinit var RADIOACTIVE_PLANKS_SWORD: Item

    // ═══════════════════════════════════════════════════════════════════
    // SCULKERITE TOOLS & WEAPONS
    // ═══════════════════════════════════════════════════════════════════
    lateinit var SCULKERITE_AXE: Item
    lateinit var SCULKERITE_HOE: Item
    lateinit var SCULKERITE_PICKAXE: Item
    lateinit var SCULKERITE_SHOVEL: Item
    lateinit var SCULKERITE_HAMMER: Item
    lateinit var SCULKERITE_LONGSWORD: Item
    lateinit var SCULKERITE_LONGSWORD2: Item
    lateinit var SCULKHERITE_PAXEL: Item
    lateinit var SCULKHERITE_SWORD2: Item

    // ═══════════════════════════════════════════════════════════════════
    // SCULKERITE ARMOR
    // ═══════════════════════════════════════════════════════════════════
    lateinit var SCULKERITE_HELMET: Item
    lateinit var SCULKERITE_CHESTPLATE: Item
    lateinit var SCULKERITE_LEGGINGS: Item
    lateinit var SCULKERITE_BOOTS: Item

    // ═══════════════════════════════════════════════════════════════════
    // WARDEN ARMOR & WEAPONS
    // ═══════════════════════════════════════════════════════════════════
    lateinit var WARDEN_HELMET: Item
    lateinit var WARDEN_CHESTPLATE: Item
    lateinit var WARDEN_LEGGINGS: Item
    lateinit var WARDEN_BOOTS: Item
    lateinit var WARDEN_SWORD: Item

    // ═══════════════════════════════════════════════════════════════════
    // SCULK ARMS (special weapons)
    // ═══════════════════════════════════════════════════════════════════
    lateinit var SCULK_ARMS2: Item
    lateinit var NETHERITE_SCULK_ARMS: Item
    lateinit var SCULKERITE_SCULK_ARMS: Item

    // ═══════════════════════════════════════════════════════════════════
    // RANGED — Sculk Bow & Arrow
    // ═══════════════════════════════════════════════════════════════════
    lateinit var SCULK_BOW: Item
    lateinit var SCULK_ARROW: Item

    // ═══════════════════════════════════════════════════════════════════
    // SPECIAL ITEMS
    // ═══════════════════════════════════════════════════════════════════
    lateinit var ANCIENT_NAUTILUS_BOOMERANG: Item
    lateinit var DEEP_DARK_PORTAL_IGNITER: Item
    lateinit var ARDAS_SCULKS_BOOK: Item

    // ═══════════════════════════════════════════════════════════════════
    // FOOD
    // ═══════════════════════════════════════════════════════════════════
    lateinit var CREAKING_COOKIE: Item
    lateinit var SWEET_PALE_CAKE: Item
    lateinit var SCULK_RESIN_APPLE: Item

    // ═══════════════════════════════════════════════════════════════════
    // BUCKETS
    // ═══════════════════════════════════════════════════════════════════
    lateinit var IRON_BUCKET2: Item
    lateinit var MOLTEN_SCULK_BUCKET: Item
    lateinit var RAW_IRON_BUCKET: Item
    lateinit var SCULK_BUCKET: Item

    // ═══════════════════════════════════════════════════════════════════
    // BLOCK ITEMS (doors, vines, decorative)
    // ═══════════════════════════════════════════════════════════════════
    lateinit var BLACK_BIRCH_DOOR_ITEM: Item
    lateinit var BLACK_BIRCH_VINES: Item
    lateinit var RADIOACTIVE_BALSA_DOOR_ITEM: Item
    lateinit var RADIOACTIVE_SCULK_VINES: Item
    lateinit var SCULK_CRYSTAL_BARS: Item
    lateinit var SCULK_CRYSTAL_DOOR_ITEM: Item
    lateinit var SCULK_PLANK_DOOR_ITEM: Item
    lateinit var WISTERYA_DOOR_ITEM: Item
    lateinit var SCULK_GRASS_FLOWERS: Item
    lateinit var LONG_SCULK_MUSHROOM_ITEM: Item
    lateinit var SCULK_CHEST_ANIMATED: Item

    // ═══════════════════════════════════════════════════════════════════
    // EXTRA SPAWN EGGS (no entity type yet — plain item placeholders)
    // ═══════════════════════════════════════════════════════════════════
    lateinit var AGILE_SLIME_SPAWN_EGG: Item
    lateinit var SCULK_CREEPER_SPAWN_EGG: Item
    lateinit var SCULK_FISH_SPAWN_EGG: Item
    lateinit var SCULK_FOX_SPAWN_EGG: Item
    lateinit var SCULK_GHOST_SPAWN_EGG: Item
    lateinit var SCULK_GOLEM_SPAWN_EGG: Item

    fun register() {
//         SOUL = register("soul") { Item(it) }
        // Portal ignition has been disabled; keep item id for save/data-pack compatibility.
//         ANCARD_LIGHTER = register("ancard_lighter") { props -> Item(props.durability(64)) }
        
        // Herobrine spawn egg - uses custom texture for colors
        HEROBRINE_SPAWN_EGG = register("herobrine_spawn_egg") { props ->
            SpawnEggItem(props.spawnEgg(AeonisEntities.HEROBRINE))
        }
        
        // Copper Stalker spawn egg - copper/orange themed
        COPPER_STALKER_SPAWN_EGG = register("copper_stalker_spawn_egg") { props ->
            SpawnEggItem(props.spawnEgg(AeonisEntities.COPPER_STALKER))
        }

        // Ancard mob system is disabled; preserve spawn egg item ids as inert items.
//         ASH_STALKER_SPAWN_EGG = register("ash_stalker_spawn_egg") { props -> Item(props) }
//         BLOODROOT_FIEND_SPAWN_EGG = register("bloodroot_fiend_spawn_egg") { props -> Item(props) }
//         VEILSHADE_WATCHER_SPAWN_EGG = register("veilshade_watcher_spawn_egg") { props -> Item(props) }
//         ANCARD_SOVEREIGN_SPAWN_EGG = register("ancard_sovereign_spawn_egg") { props -> Item(props) }
//         SHADE_LURKER_SPAWN_EGG = register("shade_lurker_spawn_egg") { props -> Item(props) }
//         OBELISK_SENTINEL_SPAWN_EGG = register("obelisk_sentinel_spawn_egg") { props -> Item(props) }
//         CRYPT_MITE_SPAWN_EGG = register("crypt_mite_spawn_egg") { props -> Item(props) }
//         BONEWEAVER_SPAWN_EGG = register("boneweaver_spawn_egg") { props -> Item(props) }
//         ECHO_WISP_SPAWN_EGG = register("echo_wisp_spawn_egg") { props -> Item(props) }
//         RUIN_HOUND_SPAWN_EGG = register("ruin_hound_spawn_egg") { props -> Item(props) }
//         VEIL_MIMIC_SPAWN_EGG = register("veil_mimic_spawn_egg") { props -> Item(props) }
//         SPOREBACK_SPAWN_EGG = register("sporeback_spawn_egg") { props -> Item(props) }
//         RIFT_SCREECHER_SPAWN_EGG = register("rift_screecher_spawn_egg") { props -> Item(props) }
//         ANCIENT_COLOSSUS_SPAWN_EGG = register("ancient_colossus_spawn_egg") { props -> Item(props) }

        HUNTER_SPAWN_EGG = register("hunter_spawn_egg") { props ->
            SpawnEggItem(props.spawnEgg(AeonisEntities.HUNTER))
        }

//         RADIOACTIVE_WARDEN_SPAWN_EGG = register("radioactive_warden_spawn_egg") { props -> Item(props) }
//         SCULK_BOSS_1_SPAWN_EGG = register("sculk_boss_1_spawn_egg") { props -> Item(props) }
//         SCULK_CREAKING_SPAWN_EGG = register("sculk_creaking_spawn_egg") { props -> Item(props) }
//         SCULK_CREEPER_ANIMATION_SPAWN_EGG = register("sculk_creeper_animation_spawn_egg") { props -> Item(props) }
//         SCULK_ENDERMAN_SPAWN_EGG = register("sculk_enderman_spawn_egg") { props -> Item(props) }
//         SCULK_GOLEM_BOSS_SPAWN_EGG = register("sculk_golem_boss_spawn_egg") { props -> Item(props) }
//         SCULK_SKELETON_SPAWN_EGG = register("sculk_skeleton_spawn_egg") { props -> Item(props) }
//         SCULK_SLIME_SPAWN_EGG = register("sculk_slime_spawn_egg") { props -> Item(props) }
        SHADOW_HUNTER_SPAWN_EGG = register("shadow_hunter_spawn_egg") { props -> Item(props) }

        // Crimson materials
//         RAW_CRIMSON = register("raw_crimson") { Item(it.fireResistant()) }
//         CRIMSON_INGOT_SCRAPS = register("crimson_ingot_scraps") { Item(it.fireResistant()) }

        // Tungsten progression (between iron and diamond)
//         TUNGSTEN_RAW = register("tungsten_raw") { Item(it) }
//         TUNGSTEN_INGOT = register("tungsten_ingot") { Item(it) }
//         TUNGSTEN_SWORD = register("tungsten_sword") { Item(it.sword(TUNGSTEN_TOOL_MATERIAL, 3.0f, -2.4f)) }
//         TUNGSTEN_PICKAXE = register("tungsten_pickaxe") { Item(it.pickaxe(TUNGSTEN_TOOL_MATERIAL, 1.0f, -2.8f)) }
//         TUNGSTEN_AXE = register("tungsten_axe") { AxeItem(TUNGSTEN_TOOL_MATERIAL, 6.0f, -3.0f, it) }
//         TUNGSTEN_SHOVEL = register("tungsten_shovel") { ShovelItem(TUNGSTEN_TOOL_MATERIAL, 1.5f, -3.0f, it) }
//         TUNGSTEN_HOE = register("tungsten_hoe") { HoeItem(TUNGSTEN_TOOL_MATERIAL, 0.0f, -3.0f, it) }

        // Companion whistle — cycles forced modes
        RHISTEL = register("rhistel") { props -> RhistelItem(props.stacksTo(1)) }

        // ═══════════════════════════════════════════════════════════════
        // SCULK MATERIALS & INGREDIENTS
        // ═══════════════════════════════════════════════════════════════
//         SCULK_PIECE = register("sculk_piece") { Item(it) }
//         SCULK_INGOT = register("sculk_ingot") { Item(it) }
//         SCULK_BONE = register("sculk_bone") { Item(it) }
//         SCULK_CRYSTAL = register("sculk_crystal") { Item(it) }
//         SCULK_SENSOR_PART = register("sculk_sensor_part") { Item(it) }
//         SCULK_RESIN_BRICK = register("sculk_resin_brick") { Item(it) }
//         SCULK_RESIN_CLUMP = register("sculk_resin_clump") { Item(it) }
//         SCULKERITE_NUGGET = register("sculkerite_nugget") { Item(it) }
//         SCULKED_EMERALD = register("sculkedemerald") { Item(it) }
//         GOLDEN_SCULK_PIECE = register("golden_sculk_piece") { Item(it) }
//         GOLDEN_SCULK_SENSOR_PART = register("golden_sculk_sensor_part") { Item(it) }
//         GOLDEN_WARDEN_CLAW = register("golden_warden_claw") { Item(it) }
//         RADIOACTIVE_SCULK_PIECE = register("radioactive_sculk_piece") { Item(it) }
//         RADIOACTIVE_SCULK_SENSOR_PART = register("radioactive_sculk_sensor_part") { Item(it) }
//         RADIOACTIVE_WARDEN_CLAW = register("radioactive_warden_claw") { Item(it) }
//         WARDEN_CLAW = register("warden_claw") { Item(it) }
//         SACRED_OBSIDIAN_SHARD = register("sacredobsidianshard") { Item(it.fireResistant()) }
//         ROTTEN_CREAKING_SHELL = register("rotten_creaking_shell") { Item(it) }
//         ANCIENT_NAUTILUS_SHELL = register("ancient_nautilus_shell") { Item(it) }
//         AGILE_SLIME_BALL = register("agile_slime_ball") { Item(it) }
//         DEEP_DARK_UPGRADE_TEMPLATE = register("deepdarkupgradetemplate") { Item(it) }
//         SCULKERITE_UPGRADE_SMITHING_TEMPLATE = register("sculkerite_upgrade_smithing_template") { Item(it) }

        // ═══════════════════════════════════════════════════════════════
        // STICKS
        // ═══════════════════════════════════════════════════════════════
//         BLACKBIRCH_STICK = register("blackbirch_stick") { Item(it) }
//         RADIOACTIVE_BALSA_STICK = register("radioactive_balsa_stick") { Item(it) }
//         SCULK_PLANKS_STICK = register("sculk_planks_stick") { Item(it) }
//         WISTERYA_STICK = register("wisterya_stick") { Item(it) }

        // ═══════════════════════════════════════════════════════════════
        // WOODEN TOOLS — Black Birch
        // ═══════════════════════════════════════════════════════════════
//         BLACKBIRCH_WOODEN_AXE = register("blackbirch_wooden_axe") { AxeItem(ToolMaterial.WOOD, 6.0f, -3.1f, it) }
//         BLACKBIRCH_WOODEN_HOE = register("blackbirch_wooden_hoe") { HoeItem(ToolMaterial.WOOD, 0.0f, -3.0f, it) }
//         BLACKBIRCH_WOODEN_PICKAXE = register("blackbirch_wooden_pickaxe") { Item(it.pickaxe(ToolMaterial.WOOD, 1.0f, -2.8f)) }
//         BLACKBIRCH_WOODEN_SHOVEL = register("blackbirch_wooden_shovel") { ShovelItem(ToolMaterial.WOOD, 1.5f, -3.0f, it) }
//         BLACKBIRCH_WOODEN_SWORD = register("blackbirch_wooden_sword") { Item(it.sword(ToolMaterial.WOOD, 3.0f, -2.4f)) }

        // ═══════════════════════════════════════════════════════════════
        // WOODEN TOOLS — Sculk
        // ═══════════════════════════════════════════════════════════════
//         SCULK_WOODEN_AXE = register("sculk_wooden_axe") { AxeItem(ToolMaterial.WOOD, 6.0f, -3.1f, it) }
//         SCULK_WOODEN_HOE = register("sculk_wooden_hoe") { HoeItem(ToolMaterial.WOOD, 0.0f, -3.0f, it) }
//         SCULK_WOODEN_PICKAXE = register("sculk_wooden_pickaxe") { Item(it.pickaxe(ToolMaterial.WOOD, 1.0f, -2.8f)) }
//         SCULK_WOODEN_SHOVEL = register("sculk_wooden_shovel") { ShovelItem(ToolMaterial.WOOD, 1.5f, -3.0f, it) }
//         SCULK_WOODEN_SWORD = register("sculk_wooden_sword") { Item(it.sword(ToolMaterial.WOOD, 3.0f, -2.4f)) }

        // ═══════════════════════════════════════════════════════════════
        // WOODEN TOOLS — Wisterya
        // ═══════════════════════════════════════════════════════════════
//         WISTERYA_WOODEN_AXE = register("wisterya_wooden_axe") { AxeItem(ToolMaterial.WOOD, 6.0f, -3.1f, it) }
//         WISTERYA_WOODEN_HOE = register("wisterya_wooden_hoe") { HoeItem(ToolMaterial.WOOD, 0.0f, -3.0f, it) }
//         WISTERYA_WOODEN_PICKAXE = register("wisterya_wooden_pickaxe") { Item(it.pickaxe(ToolMaterial.WOOD, 1.0f, -2.8f)) }
//         WISTERYA_WOODEN_SHOVEL = register("wisterya_wooden_shovel") { ShovelItem(ToolMaterial.WOOD, 1.5f, -3.0f, it) }
//         WISTERYA_WOODEN_SWORD = register("wisterya_wooden_sword") { Item(it.sword(ToolMaterial.WOOD, 3.0f, -2.4f)) }

        // ═══════════════════════════════════════════════════════════════
        // WOODEN TOOLS — Radioactive Balsa
        // ═══════════════════════════════════════════════════════════════
//         RADIOACTIVE_PLANKS_AXE = register("radioactive_planks_axe") { AxeItem(ToolMaterial.WOOD, 6.0f, -3.1f, it) }
//         RADIOACTIVE_PLANKS_HOE = register("radioactive_planks_hoe") { HoeItem(ToolMaterial.WOOD, 0.0f, -3.0f, it) }
//         RADIOACTIVE_PLANKS_PICKAXE = register("radioactive_planks_pickaxe") { Item(it.pickaxe(ToolMaterial.WOOD, 1.0f, -2.8f)) }
//         RADIOACTIVE_PLANKS_SHOVEL = register("radioactive_planks_shovel") { ShovelItem(ToolMaterial.WOOD, 1.5f, -3.0f, it) }
//         RADIOACTIVE_PLANKS_SWORD = register("radioactive_planks_sword") { Item(it.sword(ToolMaterial.WOOD, 3.0f, -2.4f)) }

        // ═══════════════════════════════════════════════════════════════
        // SCULKERITE TOOLS & WEAPONS
        // ═══════════════════════════════════════════════════════════════
//         SCULKERITE_AXE = register("sculkerite_axe") { AxeItem(ToolMaterial.NETHERITE, 7.0f, -3.0f, it) }
//         SCULKERITE_HOE = register("sculkerite_hoe") { HoeItem(ToolMaterial.NETHERITE, 0.0f, -3.0f, it) }
//         SCULKERITE_PICKAXE = register("sculkerite_pickaxe") { Item(it.pickaxe(ToolMaterial.NETHERITE, 1.0f, -2.8f)) }
//         SCULKERITE_SHOVEL = register("sculkerite_shovel") { ShovelItem(ToolMaterial.NETHERITE, 1.5f, -3.0f, it) }
//         SCULKERITE_HAMMER = register("sculkerite_hammer") { Item(it.pickaxe(ToolMaterial.NETHERITE, 6.0f, -3.2f)) }
//         SCULKERITE_LONGSWORD = register("sculkerite_longsword") { Item(it.sword(ToolMaterial.NETHERITE, 5.0f, -2.6f)) }
//         SCULKERITE_LONGSWORD2 = register("sculkerite_longsword2") { Item(it.sword(ToolMaterial.NETHERITE, 6.0f, -2.8f)) }
//         SCULKHERITE_PAXEL = register("sculkheritepaxel") { Item(it.pickaxe(ToolMaterial.NETHERITE, 5.0f, -2.8f)) }
//         SCULKHERITE_SWORD2 = register("sculkheritesword2") { Item(it.sword(ToolMaterial.NETHERITE, 6.0f, -2.6f)) }

        // ═══════════════════════════════════════════════════════════════
        // SCULKERITE ARMOR
        // ═══════════════════════════════════════════════════════════════
//         SCULKERITE_HELMET = register("sculkerite_helmet") { wearableArmor(it, ArmorType.HELMET, SCULKERITE_ARMOR_MATERIAL) }
//         SCULKERITE_CHESTPLATE = register("sculkerite_chestplate") { wearableArmor(it, ArmorType.CHESTPLATE, SCULKERITE_ARMOR_MATERIAL) }
//         SCULKERITE_LEGGINGS = register("sculkerite_leggings") { wearableArmor(it, ArmorType.LEGGINGS, SCULKERITE_ARMOR_MATERIAL) }
//         SCULKERITE_BOOTS = register("sculkerite_boots") { wearableArmor(it, ArmorType.BOOTS, SCULKERITE_ARMOR_MATERIAL) }

        // ═══════════════════════════════════════════════════════════════
        // WARDEN ARMOR & WEAPONS
        // ═══════════════════════════════════════════════════════════════
//         WARDEN_HELMET = register("warden_helmet") { wearableArmor(it, ArmorType.HELMET, WARDEN_ARMOR_MATERIAL) }
//         WARDEN_CHESTPLATE = register("warden_chestplate") { wearableArmor(it, ArmorType.CHESTPLATE, WARDEN_ARMOR_MATERIAL) }
//         WARDEN_LEGGINGS = register("warden_leggings") { wearableArmor(it, ArmorType.LEGGINGS, WARDEN_ARMOR_MATERIAL) }
//         WARDEN_BOOTS = register("warden_boots") { wearableArmor(it, ArmorType.BOOTS, WARDEN_ARMOR_MATERIAL) }
//         WARDEN_SWORD = register("wardensword") { Item(it.sword(ToolMaterial.NETHERITE, 6.0f, -2.4f)) }

        // ═══════════════════════════════════════════════════════════════
        // SCULK ARMS (special weapons)
        // ═══════════════════════════════════════════════════════════════
//         SCULK_ARMS2 = register("sculkarms2") { Item(it.sword(ToolMaterial.IRON, 4.0f, -2.6f).durability(500)) }
//         NETHERITE_SCULK_ARMS = register("netheritesculkarms") { Item(it.sword(ToolMaterial.NETHERITE, 6.0f, -2.4f).fireResistant()) }
//         SCULKERITE_SCULK_ARMS = register("sculkeritesculkarms") { Item(it.sword(ToolMaterial.NETHERITE, 5.5f, -2.5f)) }

        // ═══════════════════════════════════════════════════════════════
        // RANGED — Sculk Bow & Arrow
        // ═══════════════════════════════════════════════════════════════
//         SCULK_BOW = register("sculk_bow") { net.minecraft.world.item.BowItem(it.stacksTo(1).durability(384)) }
//         SCULK_ARROW = register("sculk_arrow") { Item(it) }

        // ═══════════════════════════════════════════════════════════════
        // SPECIAL ITEMS
        // ═══════════════════════════════════════════════════════════════
//         ANCIENT_NAUTILUS_BOOMERANG = register("ancient_nautilus_boomerang") { Item(it.stacksTo(1).durability(250)) }
//         DEEP_DARK_PORTAL_IGNITER = register("deepdarkportaligniter") { Item(it.stacksTo(1).durability(64)) }
//         ARDAS_SCULKS_BOOK = register("ardassculksbook") { Item(it.stacksTo(1)) }

        // ═══════════════════════════════════════════════════════════════
        // FOOD
        // ═══════════════════════════════════════════════════════════════
//         CREAKING_COOKIE = register("creaking_cookie") { Item(it.food(FoodProperties(4, 0.3f, false))) }
//         SWEET_PALE_CAKE = register("sweet_pale_cake") { Item(it.stacksTo(1).food(FoodProperties(6, 0.6f, false))) }
//         SCULK_RESIN_APPLE = register("sculk_resin_apple") { Item(it.food(FoodProperties(4, 1.2f, false))) }

        // ═══════════════════════════════════════════════════════════════
        // BUCKETS
        // ═══════════════════════════════════════════════════════════════
//         IRON_BUCKET2 = register("ironbucket2") { Item(it.stacksTo(1)) }
//         MOLTEN_SCULK_BUCKET = register("moltensculk_bucket") { Item(it.stacksTo(1)) }
//         RAW_IRON_BUCKET = register("rawiron_bucket") { Item(it.stacksTo(1)) }
//         SCULK_BUCKET = register("sculkbucket") { Item(it.stacksTo(1)) }

        // ═══════════════════════════════════════════════════════════════
        // BLOCK ITEMS (doors, vines, decorative)
        // ═══════════════════════════════════════════════════════════════
        BLACK_BIRCH_DOOR_ITEM = registerBlockProxy("black_birch_door_item", Blocks.DARK_OAK_DOOR, true)
        BLACK_BIRCH_VINES = registerBlockProxy("black_birch_vines", Blocks.VINE, false)
        RADIOACTIVE_BALSA_DOOR_ITEM = registerBlockProxy("radioactive_balsa_door_item", Blocks.MANGROVE_DOOR, true)
        RADIOACTIVE_SCULK_VINES = registerBlockProxy("radioactivesculkvines", Blocks.WEEPING_VINES, false)
        SCULK_CRYSTAL_BARS = registerBlockProxy("sculk_crystal_bars", Blocks.IRON_BARS, false)
        SCULK_CRYSTAL_DOOR_ITEM = registerBlockProxy("sculk_crystal_door_item", Blocks.IRON_DOOR, true)
        SCULK_PLANK_DOOR_ITEM = registerBlockProxy("sculk_plank_door_item", Blocks.SPRUCE_DOOR, true)
        WISTERYA_DOOR_ITEM = registerBlockProxy("wisterya_door_item", Blocks.CHERRY_DOOR, true)
        SCULK_GRASS_FLOWERS = registerBlockProxy("3ditemsculkgrassflowers", Blocks.SHORT_GRASS, false)
        LONG_SCULK_MUSHROOM_ITEM = registerBlockProxy("longsculkmushroomitem", Blocks.BROWN_MUSHROOM, false)
//         SCULK_CHEST_ANIMATED = register("itemremakesculkchestanimasyonlu") { Item(it.equippable(EquipmentSlot.CHEST)) }

        // ═══════════════════════════════════════════════════════════════
        // EXTRA SPAWN EGGS (placeholder items — no entity type yet)
        // ═══════════════════════════════════════════════════════════════
//         AGILE_SLIME_SPAWN_EGG = register("aigle_slime__spawn_egg") { Item(it) }
//         SCULK_CREEPER_SPAWN_EGG = register("sculk_creeper_spawn_egg") { Item(it) }
//         SCULK_FISH_SPAWN_EGG = register("sculk_fish_spawn_egg") { Item(it) }
//         SCULK_FOX_SPAWN_EGG = register("sculk_fox_spawn_egg") { Item(it) }
//         SCULK_GHOST_SPAWN_EGG = register("sculk_ghost_spawn_egg") { Item(it) }
//         SCULK_GOLEM_SPAWN_EGG = register("sculk_golem_spawn_egg") { Item(it) }

        // Creative tab injection via Fabric item-group API is temporarily disabled
        // during the 26.1 migration. Items remain registered and obtainable.
    }

    private fun register(name: String, factory: (Item.Properties) -> Item): Item {
        val id = Identifier.fromNamespaceAndPath("aeonis", name)
        val key = ResourceKey.create(Registries.ITEM, id)
        val props = Item.Properties().setId(key)
        return Registry.register(BuiltInRegistries.ITEM, id, factory(props))
    }

    private fun wearableArmor(props: Item.Properties, armorType: ArmorType, material: ArmorMaterial): Item {
        return Item(
            props
                .stacksTo(1)
                .humanoidArmor(material, armorType)
        )
    }

    private fun registerBlockProxy(name: String, block: Block, isDoor: Boolean): Item {
        return register(name) { props ->
            if (isDoor) DoubleHighBlockItem(block, props) else BlockItem(block, props)
        }
    }
}
