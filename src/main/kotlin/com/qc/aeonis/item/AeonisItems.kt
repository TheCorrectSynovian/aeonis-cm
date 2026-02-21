package com.qc.aeonis.item

import com.qc.aeonis.entity.AeonisEntities
import com.qc.aeonis.entity.ancard.AncardEntities
import com.qc.aeonis.entity.ancard.arda.AncardArdaEntities
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

    fun register() {
        SOUL = register("soul") { Item(it) }
        ANCARD_LIGHTER = register("ancard_lighter") { props ->
            AncardLighterItem(props.durability(64))
        }
        
        // Herobrine spawn egg - uses custom texture for colors
        HEROBRINE_SPAWN_EGG = register("herobrine_spawn_egg") { props ->
            SpawnEggItem(props.spawnEgg(AeonisEntities.HEROBRINE))
        }
        
        // Copper Stalker spawn egg - copper/orange themed
        COPPER_STALKER_SPAWN_EGG = register("copper_stalker_spawn_egg") { props ->
            SpawnEggItem(props.spawnEgg(AeonisEntities.COPPER_STALKER))
        }

        // Ancard dimension mob spawn eggs
        ASH_STALKER_SPAWN_EGG = register("ash_stalker_spawn_egg") { props ->
            SpawnEggItem(props.spawnEgg(AncardEntities.ASH_STALKER))
        }
        
        BLOODROOT_FIEND_SPAWN_EGG = register("bloodroot_fiend_spawn_egg") { props ->
            SpawnEggItem(props.spawnEgg(AncardEntities.BLOODROOT_FIEND))
        }
        
        VEILSHADE_WATCHER_SPAWN_EGG = register("veilshade_watcher_spawn_egg") { props ->
            SpawnEggItem(props.spawnEgg(AncardEntities.VEILSHADE_WATCHER))
        }
        
        ANCARD_SOVEREIGN_SPAWN_EGG = register("ancard_sovereign_spawn_egg") { props ->
            SpawnEggItem(props.spawnEgg(AncardEntities.ANCARD_SOVEREIGN))
        }

        SHADE_LURKER_SPAWN_EGG = register("shade_lurker_spawn_egg") { props ->
            SpawnEggItem(props.spawnEgg(AncardEntities.SHADE_LURKER))
        }

        OBELISK_SENTINEL_SPAWN_EGG = register("obelisk_sentinel_spawn_egg") { props ->
            SpawnEggItem(props.spawnEgg(AncardEntities.OBELISK_SENTINEL))
        }

        CRYPT_MITE_SPAWN_EGG = register("crypt_mite_spawn_egg") { props ->
            SpawnEggItem(props.spawnEgg(AncardEntities.CRYPT_MITE))
        }

        BONEWEAVER_SPAWN_EGG = register("boneweaver_spawn_egg") { props ->
            SpawnEggItem(props.spawnEgg(AncardEntities.BONEWEAVER))
        }

        ECHO_WISP_SPAWN_EGG = register("echo_wisp_spawn_egg") { props ->
            SpawnEggItem(props.spawnEgg(AncardEntities.ECHO_WISP))
        }

        RUIN_HOUND_SPAWN_EGG = register("ruin_hound_spawn_egg") { props ->
            SpawnEggItem(props.spawnEgg(AncardEntities.RUIN_HOUND))
        }

        VEIL_MIMIC_SPAWN_EGG = register("veil_mimic_spawn_egg") { props ->
            SpawnEggItem(props.spawnEgg(AncardEntities.VEIL_MIMIC))
        }

        SPOREBACK_SPAWN_EGG = register("sporeback_spawn_egg") { props ->
            SpawnEggItem(props.spawnEgg(AncardEntities.SPOREBACK))
        }

        RIFT_SCREECHER_SPAWN_EGG = register("rift_screecher_spawn_egg") { props ->
            SpawnEggItem(props.spawnEgg(AncardEntities.RIFT_SCREECHER))
        }

        ANCIENT_COLOSSUS_SPAWN_EGG = register("ancient_colossus_spawn_egg") { props ->
            SpawnEggItem(props.spawnEgg(AncardEntities.ANCIENT_COLOSSUS))
        }

        HUNTER_SPAWN_EGG = register("hunter_spawn_egg") { props ->
            SpawnEggItem(props.spawnEgg(AeonisEntities.HUNTER))
        }

        // Arda Sculk spawn eggs
        RADIOACTIVE_WARDEN_SPAWN_EGG = register("radioactive_warden_spawn_egg") { props ->
            SpawnEggItem(props.spawnEgg(AncardArdaEntities.RADIOACTIVE_WARDEN))
        }

        SCULK_BOSS_1_SPAWN_EGG = register("sculk_boss_1_spawn_egg") { props ->
            SpawnEggItem(props.spawnEgg(AncardArdaEntities.SCULK_BOSS_1))
        }

        SCULK_CREAKING_SPAWN_EGG = register("sculk_creaking_spawn_egg") { props ->
            SpawnEggItem(props.spawnEgg(AncardArdaEntities.SCULK_CREAKING))
        }

        SCULK_CREEPER_ANIMATION_SPAWN_EGG = register("sculk_creeper_animation_spawn_egg") { props ->
            SpawnEggItem(props.spawnEgg(AncardArdaEntities.SCULK_CREEPER_ANIMATION))
        }

        SCULK_ENDERMAN_SPAWN_EGG = register("sculk_enderman_spawn_egg") { props ->
            SpawnEggItem(props.spawnEgg(AncardArdaEntities.SCULK_ENDERMAN))
        }

        SCULK_GOLEM_BOSS_SPAWN_EGG = register("sculk_golem_boss_spawn_egg") { props ->
            SpawnEggItem(props.spawnEgg(AncardArdaEntities.SCULK_GOLEM_BOSS))
        }

        SCULK_SKELETON_SPAWN_EGG = register("sculk_skeleton_spawn_egg") { props ->
            SpawnEggItem(props.spawnEgg(AncardArdaEntities.SCULK_SKELETON))
        }

        SCULK_SLIME_SPAWN_EGG = register("sculk_slime_spawn_egg") { props ->
            SpawnEggItem(props.spawnEgg(AncardArdaEntities.SCULK_SLIME))
        }

        SHADOW_HUNTER_SPAWN_EGG = register("shadow_hunter_spawn_egg") { props ->
            SpawnEggItem(props.spawnEgg(AncardArdaEntities.SHADOW_HUNTER))
        }

        // Crimson materials
        RAW_CRIMSON = register("raw_crimson") { Item(it.fireResistant()) }
        CRIMSON_INGOT_SCRAPS = register("crimson_ingot_scraps") { Item(it.fireResistant()) }

        // Add items to a creative tab
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(net.minecraft.world.item.CreativeModeTabs.TOOLS_AND_UTILITIES).register { entries ->
            entries.accept(SOUL)
            entries.accept(ANCARD_LIGHTER)
        }

        // Add crimson materials to ingredients tab
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(net.minecraft.world.item.CreativeModeTabs.INGREDIENTS).register { entries ->
            entries.accept(RAW_CRIMSON)
            entries.accept(CRIMSON_INGOT_SCRAPS)
        }
        
        // Add spawn egg to spawn eggs tab
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(net.minecraft.world.item.CreativeModeTabs.SPAWN_EGGS).register { entries ->
            entries.accept(HEROBRINE_SPAWN_EGG)
            entries.accept(COPPER_STALKER_SPAWN_EGG)
            entries.accept(ASH_STALKER_SPAWN_EGG)
            entries.accept(BLOODROOT_FIEND_SPAWN_EGG)
            entries.accept(VEILSHADE_WATCHER_SPAWN_EGG)
            entries.accept(ANCARD_SOVEREIGN_SPAWN_EGG)
            entries.accept(SHADE_LURKER_SPAWN_EGG)
            entries.accept(OBELISK_SENTINEL_SPAWN_EGG)
            entries.accept(CRYPT_MITE_SPAWN_EGG)
            entries.accept(BONEWEAVER_SPAWN_EGG)
            entries.accept(ECHO_WISP_SPAWN_EGG)
            entries.accept(RUIN_HOUND_SPAWN_EGG)
            entries.accept(VEIL_MIMIC_SPAWN_EGG)
            entries.accept(SPOREBACK_SPAWN_EGG)
            entries.accept(RIFT_SCREECHER_SPAWN_EGG)
            entries.accept(ANCIENT_COLOSSUS_SPAWN_EGG)
            entries.accept(HUNTER_SPAWN_EGG)
            entries.accept(RADIOACTIVE_WARDEN_SPAWN_EGG)
            entries.accept(SCULK_BOSS_1_SPAWN_EGG)
            entries.accept(SCULK_CREAKING_SPAWN_EGG)
            entries.accept(SCULK_CREEPER_ANIMATION_SPAWN_EGG)
            entries.accept(SCULK_ENDERMAN_SPAWN_EGG)
            entries.accept(SCULK_GOLEM_BOSS_SPAWN_EGG)
            entries.accept(SCULK_SKELETON_SPAWN_EGG)
            entries.accept(SCULK_SLIME_SPAWN_EGG)
            entries.accept(SHADOW_HUNTER_SPAWN_EGG)
        }
    }

    private fun register(name: String, factory: (Item.Properties) -> Item): Item {
        val id = Identifier.fromNamespaceAndPath("aeonis", name)
        val key = ResourceKey.create(Registries.ITEM, id)
        val props = Item.Properties().setId(key)
        return Registry.register(BuiltInRegistries.ITEM, id, factory(props))
    }
}
