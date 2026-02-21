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

    fun register() {
        ANCARD_PORTAL = register("ancard_portal") { props ->
            AncardPortalBlock(
                props
                    .noCollision()
                    .strength(-1.0F, 3600000.0F)
                    .lightLevel { 3 }
                    .sound(SoundType.GLASS)
                    .noLootTable()
            )
        }
    }

    private fun register(name: String, factory: (BlockBehaviour.Properties) -> Block): Block {
        val id = Identifier.fromNamespaceAndPath("aeonis", name)
        val key = ResourceKey.create(Registries.BLOCK, id)
        val props = BlockBehaviour.Properties.of().setId(key)
        return Registry.register(BuiltInRegistries.BLOCK, id, factory(props))
    }
}
