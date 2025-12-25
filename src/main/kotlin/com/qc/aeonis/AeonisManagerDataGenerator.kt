package com.qc.aeonis

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider
import net.minecraft.core.HolderLookup
import java.util.concurrent.CompletableFuture

object AeonisManagerDataGenerator : DataGeneratorEntrypoint {
    override fun onInitializeDataGenerator(fabricDataGenerator: FabricDataGenerator) {
        val pack = fabricDataGenerator.createPack()
        pack.addProvider(::AeonisEnglishLangProvider)
    }
}

private class AeonisEnglishLangProvider(
    output: FabricDataOutput,
    registryLookup: CompletableFuture<HolderLookup.Provider>
) : FabricLanguageProvider(output, "en_us", registryLookup) {
    override fun generateTranslations(lookup: HolderLookup.Provider, builder: TranslationBuilder) {
        builder.add("itemGroup.aeonis_manager", "Aeonis Companion")
    }
}
