package com.qc.aeonis.integration.modmenu

import com.qc.aeonis.screen.AeonisPlaceholderConfigScreen
import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi

class AeonisModMenuIntegration : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> {
        return ConfigScreenFactory { parent -> AeonisPlaceholderConfigScreen(parent) }
    }
}

