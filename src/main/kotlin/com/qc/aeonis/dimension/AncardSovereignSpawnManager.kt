package com.qc.aeonis.dimension

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import com.qc.aeonis.entity.ancard.AncardEntities
import com.qc.aeonis.entity.ancard.AncardSovereignEntity
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.level.saveddata.SavedDataType
import net.minecraft.world.phys.AABB
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Ensures the Ancard Sovereign is created only once per world and only at Ancard center (x/z = 0).
 */
object AncardSovereignSpawnManager {
    private val LOGGER = LoggerFactory.getLogger("aeonis-sovereign-spawn")
    private const val CHECK_INTERVAL_TICKS = 100
    private const val SAVED_DATA_ID = "aeonis_ancard_sovereign_spawn"
    private const val WORLD_EDGE = 29_999_984.0

    private val STATE_TYPE = SavedDataType(
        SAVED_DATA_ID,
        ::SovereignSpawnState,
        SovereignSpawnState.CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    )

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register(::onServerTick)
    }

    private fun onServerTick(server: MinecraftServer) {
        if ((server.tickCount % CHECK_INTERVAL_TICKS) != 0) return

        val ancard = server.getLevel(AeonisDimensions.ANCARD) ?: return
        val state = server.overworld().dataStorage.computeIfAbsent(STATE_TYPE)

        val sovereigns = ancard.getEntitiesOfClass(
            AncardSovereignEntity::class.java,
            AABB(
                -WORLD_EDGE, ancard.minY.toDouble(), -WORLD_EDGE,
                WORLD_EDGE, ancard.maxY.toDouble(), WORLD_EDGE
            )
        )

        val active = sovereigns.firstOrNull { !it.isRemoved && it.isAlive }
        if (active != null) {
            state.hasSpawned = true
            state.sovereignUuid = active.uuid
            state.setDirty()

            if (sovereigns.size > 1) {
                sovereigns
                    .asSequence()
                    .filter { it !== active }
                    .forEach { it.discard() }
            }
            return
        }

        if (state.hasSpawned) return

        val surfaceY = ancard.getHeight(
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            0,
            0
        )
        val spawnPos = BlockPos(0, surfaceY.coerceAtLeast(ancard.minY + 1), 0)

        val sovereign = AncardEntities.ANCARD_SOVEREIGN.create(
            ancard,
            null,
            spawnPos,
            EntitySpawnReason.EVENT,
            false,
            false
        ) ?: return
        sovereign.setPos(spawnPos.x + 0.5, spawnPos.y.toDouble(), spawnPos.z + 0.5)
        ancard.addFreshEntity(sovereign)

        state.hasSpawned = true
        state.sovereignUuid = sovereign.uuid
        state.setDirty()

        LOGGER.info("Spawned Ancard Sovereign once at Ancard center near {}", spawnPos)
    }

    private class SovereignSpawnState(
        var hasSpawned: Boolean = false,
        sovereignUuidRaw: String = ""
    ) : SavedData() {
        var sovereignUuid: UUID? = sovereignUuidRaw.toUuidOrNull()

        companion object {
            val CODEC: Codec<SovereignSpawnState> = RecordCodecBuilder.create { inst ->
                inst.group(
                    Codec.BOOL.optionalFieldOf("has_spawned", false).forGetter { it.hasSpawned },
                    Codec.STRING.optionalFieldOf("sovereign_uuid", "").forGetter { it.sovereignUuid?.toString() ?: "" }
                ).apply(inst, ::SovereignSpawnState)
            }
        }
    }
}

private fun String.toUuidOrNull(): UUID? = runCatching(UUID::fromString).getOrNull()
