package com.qc.aeonis

import com.qc.aeonis.entity.AeonisEntities
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry

object AeonisEntityRenderers {

    fun register() {
        EntityRendererRegistry.register(AeonisEntities.COPPER_STALKER) { ctx ->
            CopperStalkerEntityRenderer(ctx)
        }
        // Register the Body entity renderer (uses player skin when available)
        EntityRendererRegistry.register(AeonisEntities.BODY) { ctx ->
            BodyEntityRenderer(ctx)
        }
        // Register Herobrine entity renderer (uses humanoid player model)
        EntityRendererRegistry.register(AeonisEntities.HEROBRINE) { ctx ->
            HerobrineEntityRenderer(ctx)
        }
        // Register Hunter entity renderer for Manhunt minigame
        EntityRendererRegistry.register(AeonisEntities.HUNTER) { ctx ->
            HunterEntityRenderer(ctx)
        }
        EntityRendererRegistry.register(AeonisEntities.COMPANION_BOT) { ctx ->
            CompanionBotEntityRenderer(ctx)
        }
    }
}
