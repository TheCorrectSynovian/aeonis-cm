package com.qc.aeonis

import com.qc.aeonis.client.AeonisKeyBindings
import com.qc.aeonis.network.AeonisClientNetworking
import net.fabricmc.api.ClientModInitializer

object AeonisManagerClient : ClientModInitializer {
	override fun onInitializeClient() {
		AeonisKeyBindings.register()
		AeonisClientNetworking.register()
		AeonisEntityRenderers.register()
	}
}
