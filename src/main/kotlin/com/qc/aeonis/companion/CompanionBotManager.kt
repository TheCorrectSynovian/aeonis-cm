package com.qc.aeonis.companion

import com.qc.aeonis.entity.CompanionBotEntity
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.levelgen.Heightmap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CompanionBotManager {
    private data class BotRecord(
        var entityId: Int? = null,
        var entityUuid: UUID? = null,
        var respawnCooldown: Int = 0,
        var lastKnownDimension: net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>? = null,
        var snapshot: CompanionBotEntity.Companion.CompanionSnapshot? = null
    )

    private val ownerBots = ConcurrentHashMap<UUID, BotRecord>()

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            tick(server)
        }
    }

    fun spawnOrReplace(owner: ServerPlayer): CompanionBotEntity? {
        val record = ownerBots.computeIfAbsent(owner.uuid) { BotRecord() }
        val level = owner.level() as? ServerLevel ?: return null
        val server = level.server

        findTrackedBot(server, record)?.let { existing ->
            if (existing.isAlive) {
                record.snapshot = existing.createSnapshot()
            }
            existing.discard()
        }

        val spawnPos = owner.blockPosition()
        val bot = CompanionBotEntity.spawn(level, spawnPos, owner) ?: return null
        record.snapshot?.let { bot.applySnapshot(it) }

        record.entityId = bot.id
        record.entityUuid = bot.uuid
        record.respawnCooldown = 0
        record.lastKnownDimension = level.dimension()
        return bot
    }

    fun getBot(owner: ServerPlayer): CompanionBotEntity? {
        val record = ownerBots[owner.uuid] ?: return null
        val level = owner.level() as? ServerLevel ?: return null
        return findTrackedBot(level.server, record)
    }

    fun recall(owner: ServerPlayer): Boolean {
        val bot = getBot(owner) ?: return false
        bot.recallNow()
        return true
    }

    fun dismiss(owner: ServerPlayer): Boolean {
        val record = ownerBots.remove(owner.uuid) ?: return false
        val level = owner.level() as? ServerLevel ?: return false
        val bot = findTrackedBot(level.server, record)
        bot?.discard()
        return true
    }

    private fun tick(server: MinecraftServer) {
        ownerBots.forEach { (ownerUuid, record) ->
            val owner = server.playerList.getPlayer(ownerUuid) ?: return@forEach
            val level = owner.level() as? ServerLevel ?: return@forEach

            if (record.respawnCooldown > 0) {
                record.respawnCooldown--
                return@forEach
            }

            val bot = findTrackedBot(server, record)
            if (bot != null && bot.isAlive) {
                if (bot.level() != level) {
                    record.snapshot = bot.createSnapshot()
                    bot.discard()

                    val transferPos = owner.blockPosition()
                    val transferred = CompanionBotEntity.spawn(level, transferPos, owner) ?: return@forEach
                    record.snapshot?.let { transferred.applySnapshot(it) }
                    record.entityId = transferred.id
                    record.entityUuid = transferred.uuid
                    record.lastKnownDimension = level.dimension()
                    record.respawnCooldown = 20
                    return@forEach
                }

                if (bot.distanceTo(owner) > 64.0) {
                    bot.recallNow()
                }
                record.lastKnownDimension = level.dimension()
                record.snapshot = bot.createSnapshot()
                record.entityId = bot.id
                record.entityUuid = bot.uuid
                return@forEach
            }

            val respawnPos = getOwnerRespawnPos(owner) ?: worldSpawnPos(level)
            val newBot = CompanionBotEntity.spawn(level, respawnPos, owner) ?: return@forEach
            record.snapshot?.let { newBot.applySnapshot(it) }
            record.entityId = newBot.id
            record.entityUuid = newBot.uuid
            record.lastKnownDimension = level.dimension()
            record.respawnCooldown = 40
            owner.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a[Companion] §7Your helper bot has respawned."))
        }
    }

    private fun findTrackedBot(server: MinecraftServer, record: BotRecord): CompanionBotEntity? {
        val uuid = record.entityUuid
        if (uuid != null) {
            for (level in server.allLevels) {
                val entity = level.getEntity(uuid)
                if (entity is CompanionBotEntity) return entity
            }
        }

        val id = record.entityId
        if (id != null) {
            for (level in server.allLevels) {
                val entity = level.getEntity(id)
                if (entity is CompanionBotEntity) return entity
            }
        }
        return null
    }

    private fun getOwnerRespawnPos(owner: ServerPlayer): BlockPos? {
        return try {
            val method = owner.javaClass.methods.firstOrNull { it.name == "getRespawnPosition" && it.parameterCount == 0 }
            method?.invoke(owner) as? BlockPos
        } catch (_: Throwable) {
            null
        }
    }

    private fun worldSpawnPos(level: ServerLevel): BlockPos {
        val y = level.getHeight(Heightmap.Types.WORLD_SURFACE, 0, 0)
        return BlockPos(0, y, 0)
    }
}
