package com.qc.aeonis.integration.modmenu

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi

class AeonisModMenuIntegration : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> {
        // Config UI is temporarily disabled; return to the previous screen.
        return ConfigScreenFactory { parent -> parent }
    }
}

