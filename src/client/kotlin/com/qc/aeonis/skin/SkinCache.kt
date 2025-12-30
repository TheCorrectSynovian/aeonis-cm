package com.qc.aeonis.skin

import net.minecraft.resources.ResourceLocation
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Lightweight skin cache that attempts to obtain a player's skin ResourceLocation.
 * Uses reflection to remain resilient across mappings where the player-skin API
 * types may vary. Fetching is done asynchronously; callers should check cache
 * and then request an async fetch when missing.
 */
object SkinCache {
    private val cache = ConcurrentHashMap<UUID, ResourceLocation?>()
    private val pending = ConcurrentHashMap.newKeySet<UUID>()

    fun getCached(uuid: UUID): ResourceLocation? = cache[uuid]

    fun ensureFetchAsync(uuid: UUID) {
        if (cache.containsKey(uuid) || pending.contains(uuid)) return
        pending.add(uuid)
        thread(name = "aeonis-skin-fetch-$uuid", isDaemon = true) {
            try {
                // Try DefaultPlayerSkin.getDefaultSkin(UUID) (may return ResourceLocation or a PlayerSkin-like object)
                val cls = try { Class.forName("net.minecraft.client.resources.DefaultPlayerSkin") } catch (_: Exception) { null }
                var result: ResourceLocation? = null
                if (cls != null) {
                    try {
                        val m = cls.getMethod("getDefaultSkin", UUID::class.java)
                        val obj = m.invoke(null, uuid) ?: null
                        if (obj is ResourceLocation) {
                            result = obj
                        } else if (obj != null) {
                            // Try to find a method that returns ResourceLocation
                            for (method in obj.javaClass.methods) {
                                if (method.parameterCount == 0 && method.returnType == ResourceLocation::class.java) {
                                    try { result = method.invoke(obj) as ResourceLocation; break } catch (_: Exception) { }
                                }
                            }
                            // Try fields
                            if (result == null) {
                                for (field in obj.javaClass.declaredFields) {
                                    if (field.type == ResourceLocation::class.java) {
                                        try { field.isAccessible = true; result = field.get(obj) as? ResourceLocation; break } catch (_: Exception) { }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // ignore
                    }
                }
                // As a fallback, try to load a default static texture if the reflection path failed
                if (result == null) {
                    try {
                        result = ResourceLocation.fromNamespaceAndPath("aeonis", "textures/entity/body.png")
                    } catch (_: Exception) { result = null }
                }
                cache[uuid] = result
            } finally {
                pending.remove(uuid)
            }
        }
    }
}