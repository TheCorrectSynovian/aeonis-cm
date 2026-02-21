package com.qc.aeonis

import com.qc.aeonis.block.AeonisBlocks
import com.qc.aeonis.block.entity.AeonisBlockEntities
import com.qc.aeonis.client.block.SafeChestRenderer
import com.qc.aeonis.llm.LlmClientNetworking
import com.qc.aeonis.network.AeonisClientNetworking
import com.qc.aeonis.client.AeonisKeyBindings
import com.qc.aeonis.client.dimension.AncardDimensionRenderer
import com.qc.aeonis.client.dimension.AncardPostProcessing
import com.qc.aeonis.client.dimension.AncardLightningEvents
import com.qc.aeonis.client.entity.AncardEntityRenderers
import com.qc.aeonis.client.ancard.AncardFogEnvironment
import com.qc.aeonis.mixin.client.AncardFogMixin
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers
import net.minecraft.client.renderer.chunk.ChunkSectionLayer

object AeonisManagerClient : ClientModInitializer {
	override fun onInitializeClient() {
		AeonisEntityRenderers.register()
		AncardEntityRenderers.register()
		// Register key bindings first (safe time during client init)
		AeonisKeyBindings.register()
		AeonisClientNetworking.register()
		
		// Register LLM client networking
		LlmClientNetworking.register()

		// Register Ancard dimension rendering
		AncardDimensionRenderer.register()
		AncardPostProcessing.register()
		AncardLightningEvents.register()

		// Register Ancard fog environment (insert before AtmosphericFogEnvironment which is last)
		val fogEnvironments = AncardFogMixin.`aeonis$getFogEnvironments`()
		fogEnvironments.add(fogEnvironments.size - 1, AncardFogEnvironment())

		// Register safe chest block entity renderer (drawer animation)
		BlockEntityRenderers.register(AeonisBlockEntities.SAFE_CHEST) { ctx -> SafeChestRenderer(ctx) }

        // Transparent bright portal rendering.
        BlockRenderLayerMap.putBlock(AeonisBlocks.ANCARD_PORTAL, ChunkSectionLayer.TRANSLUCENT)
		
		// Note: In 1.21+, spawn egg colors are handled via textures, not color providers
		// Create a custom spawn egg texture at: assets/aeonis/textures/item/herobrine_spawn_egg.png
	}
}
