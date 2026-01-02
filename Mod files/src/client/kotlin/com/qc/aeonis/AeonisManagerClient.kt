package com.qc.aeonis

import com.qc.aeonis.network.AeonisClientNetworking
import net.fabricmc.api.ClientModInitializer

object AeonisManagerClient : ClientModInitializer {
	override fun onInitializeClient() {
		AeonisEntityRenderers.register()
		AeonisClientNetworking.register()
		
		// Note: In 1.21+, spawn egg colors are handled via textures, not color providers
		// Create a custom spawn egg texture at: assets/aeonis/textures/item/herobrine_spawn_egg.png
	}
}