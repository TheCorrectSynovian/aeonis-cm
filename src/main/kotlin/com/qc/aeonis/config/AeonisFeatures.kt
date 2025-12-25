package com.qc.aeonis.config

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.storage.LevelResource
import java.io.File
import java.util.Properties
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-world feature toggles for Aeonis, stored in world folder
 */
object AeonisFeatures {
    
    // Cache features per world (keyed by world folder path)
    private val worldFeatures = ConcurrentHashMap<String, WorldFeatures>()
    
    data class WorldFeatures(
        var extraMobsEnabled: Boolean = false,
        val seenWelcome: MutableSet<UUID> = mutableSetOf()
    )
    
    private fun getWorldKey(server: MinecraftServer): String {
        return server.getWorldPath(LevelResource.ROOT).toString()
    }
    
    private fun getConfigFile(server: MinecraftServer): File {
        val worldPath = server.getWorldPath(LevelResource.ROOT)
        return worldPath.resolve("aeonis_features.properties").toFile()
    }
    
    private fun loadFeatures(server: MinecraftServer): WorldFeatures {
        val key = getWorldKey(server)
        
        return worldFeatures.getOrPut(key) {
            val features = WorldFeatures()
            val configFile = getConfigFile(server)
            
            if (configFile.exists()) {
                try {
                    val props = Properties()
                    configFile.inputStream().use { props.load(it) }
                    
                    features.extraMobsEnabled = props.getProperty("extra_mobs_enabled", "false").toBoolean()
                    
                    // Load seen welcome UUIDs
                    val seenStr = props.getProperty("seen_welcome", "")
                    if (seenStr.isNotEmpty()) {
                        seenStr.split(",").forEach { uuidStr ->
                            try {
                                features.seenWelcome.add(UUID.fromString(uuidStr.trim()))
                            } catch (_: Exception) {}
                        }
                    }
                } catch (e: Exception) {
                    // Ignore load errors, use defaults
                }
            }
            
            features
        }
    }
    
    private fun saveFeatures(server: MinecraftServer) {
        val key = getWorldKey(server)
        val features = worldFeatures[key] ?: return
        val configFile = getConfigFile(server)
        
        try {
            val props = Properties()
            props.setProperty("extra_mobs_enabled", features.extraMobsEnabled.toString())
            props.setProperty("seen_welcome", features.seenWelcome.joinToString(",") { it.toString() })
            
            configFile.outputStream().use { props.store(it, "Aeonis Features Config") }
        } catch (e: Exception) {
            // Ignore save errors
        }
    }
    
    fun get(server: MinecraftServer): WorldFeatures {
        return loadFeatures(server)
    }
    
    fun get(level: ServerLevel): WorldFeatures {
        return get(level.server)
    }
    
    fun isExtraMobsEnabled(server: MinecraftServer): Boolean {
        return loadFeatures(server).extraMobsEnabled
    }
    
    fun areExtraMobsEnabled(level: ServerLevel): Boolean {
        return isExtraMobsEnabled(level.server)
    }
    
    fun setExtraMobsEnabled(server: MinecraftServer, enabled: Boolean) {
        loadFeatures(server).extraMobsEnabled = enabled
        saveFeatures(server)
    }
    
    fun hasSeenWelcome(server: MinecraftServer, uuid: UUID): Boolean {
        return loadFeatures(server).seenWelcome.contains(uuid)
    }
    
    fun markWelcomeSeen(server: MinecraftServer, uuid: UUID) {
        loadFeatures(server).seenWelcome.add(uuid)
        saveFeatures(server)
    }
}
