package com.qc.aeonis.block.entity

import com.qc.aeonis.block.AeonisBlocks
import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityTypeBuilder
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.entity.BlockEntityType

object AeonisBlockEntities {
    lateinit var SAFE_CHEST: BlockEntityType<SafeChestBlockEntity>

    fun register() {
        SAFE_CHEST = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Identifier.fromNamespaceAndPath("aeonis", "safe_chest"),
            FabricBlockEntityTypeBuilder.create(::SafeChestBlockEntity, AeonisBlocks.SAFE_CHEST).build()
        )
    }
}

