package com.qc.aeonis

import com.qc.aeonis.network.AeonisClientNetworking
import net.fabricmc.api.ClientModInitializer

object AeonisManagerClient : ClientModInitializer {
	override fun onInitializeClient() {
		AeonisEntityRenderers.register()
		AeonisClientNetworking.register()
	}
}