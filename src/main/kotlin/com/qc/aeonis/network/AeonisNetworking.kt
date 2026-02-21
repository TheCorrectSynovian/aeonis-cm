package com.qc.aeonis.network

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.entity.monster.skeleton.Skeleton
import net.minecraft.world.entity.animal.wolf.Wolf
import net.minecraft.world.entity.animal.golem.IronGolem
import net.minecraft.world.entity.animal.allay.Allay
import net.minecraft.world.entity.animal.bee.Bee
import net.minecraft.world.entity.animal.parrot.Parrot
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase
import net.minecraft.world.entity.monster.Blaze
import net.minecraft.world.entity.monster.Ghast
import net.minecraft.world.entity.monster.Phantom
import net.minecraft.world.entity.monster.Vex
import net.minecraft.world.entity.monster.EnderMan
import net.minecraft.world.entity.monster.Creeper
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton
import net.minecraft.world.entity.monster.breeze.Breeze
import net.minecraft.world.entity.monster.illager.Pillager
import net.minecraft.world.entity.animal.chicken.Chicken
import net.minecraft.world.entity.projectile.arrow.Arrow
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.BreezeWindCharge
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.Witch
import net.minecraft.world.entity.monster.illager.Evoker
import net.minecraft.world.entity.monster.warden.Warden
import net.minecraft.world.entity.animal.golem.SnowGolem
import net.minecraft.world.entity.projectile.EvokerFangs
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import com.qc.aeonis.AeonisPossession
import com.qc.aeonis.command.AeonisCommands

/**
 * Packet sent from client to server with movement input
 */
data class MobControlPayload(
    val forward: Float,
    val strafe: Float,
    val jump: Boolean,
    val sneak: Boolean,
    val yaw: Float,
    val pitch: Float,
    val attack: Boolean,
    val teleport: Boolean = false
) : CustomPacketPayload {
    
    companion object {
        val ID = CustomPacketPayload.Type<MobControlPayload>(
            Identifier.fromNamespaceAndPath("aeonis-manager", "mob_control")
        )
        
        val CODEC: StreamCodec<FriendlyByteBuf, MobControlPayload> = StreamCodec.of(
            { buf, payload ->
                buf.writeFloat(payload.forward)
                buf.writeFloat(payload.strafe)
                buf.writeBoolean(payload.jump)
                buf.writeBoolean(payload.sneak)
                buf.writeFloat(payload.yaw)
                buf.writeFloat(payload.pitch)
                buf.writeBoolean(payload.attack)
                buf.writeBoolean(payload.teleport)
            },
            { buf ->
                MobControlPayload(
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readBoolean(),
                    buf.readBoolean()
                )
            }
        )
    }
    
    override fun type(): CustomPacketPayload.Type<MobControlPayload> = ID
}

/**
 * Packet sent from server to client to enable/disable control mode
 */
data class ControlModePayload(
    val enabled: Boolean,
    val mobId: Int = -1,
    val selectedSlot: Int = -1
) : CustomPacketPayload {
    
    companion object {
        val ID = CustomPacketPayload.Type<ControlModePayload>(
            Identifier.fromNamespaceAndPath("aeonis-manager", "control_mode")
        )
        
        val CODEC: StreamCodec<FriendlyByteBuf, ControlModePayload> = StreamCodec.of(
            { buf, payload -> 
                buf.writeBoolean(payload.enabled)
                buf.writeInt(payload.mobId)
                buf.writeInt(payload.selectedSlot)
            },
            { buf -> ControlModePayload(buf.readBoolean(), buf.readInt(), buf.readInt()) }
        )
    }
    
    override fun type(): CustomPacketPayload.Type<ControlModePayload> = ID
}

/**
 * Possess / Release / Ability payloads
 */
data class PossessPayload(
    val mobId: Int,
    val selectedSlot: Int
) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<PossessPayload>(
            Identifier.fromNamespaceAndPath("aeonis-manager", "possess_mob")
        )
        val CODEC: StreamCodec<FriendlyByteBuf, PossessPayload> = StreamCodec.of(
            { buf, payload ->
                buf.writeInt(payload.mobId)
                buf.writeInt(payload.selectedSlot)
            },
            { buf -> PossessPayload(buf.readInt(), buf.readInt()) }
        )
    }
    override fun type(): CustomPacketPayload.Type<PossessPayload> = ID
}

data class ReleasePayload(
    val selectedSlot: Int,
    val bodyX: Double,
    val bodyY: Double,
    val bodyZ: Double
) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<ReleasePayload>(
            Identifier.fromNamespaceAndPath("aeonis-manager", "release_mob")
        )
        val CODEC: StreamCodec<FriendlyByteBuf, ReleasePayload> = StreamCodec.of(
            { buf, payload ->
                buf.writeInt(payload.selectedSlot)
                buf.writeDouble(payload.bodyX)
                buf.writeDouble(payload.bodyY)
                buf.writeDouble(payload.bodyZ)
            },
            { buf -> ReleasePayload(buf.readInt(), buf.readDouble(), buf.readDouble(), buf.readDouble()) }
        )
    }
    override fun type(): CustomPacketPayload.Type<ReleasePayload> = ID
}

class AbilityPayload : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<AbilityPayload>(
            Identifier.fromNamespaceAndPath("aeonis-manager", "ability")
        )
        val CODEC: StreamCodec<FriendlyByteBuf, AbilityPayload> = StreamCodec.of(
            { _, _ -> },
            { _ -> AbilityPayload() }
        )
    }
    override fun type(): CustomPacketPayload.Type<AbilityPayload> = ID
}

// Selected slot sync payload (client -> server)
data class SelectedSlotPayload(val selectedSlot: Int) : CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<SelectedSlotPayload>(
            Identifier.fromNamespaceAndPath("aeonis-manager", "selected_slot")
        )
        val CODEC: StreamCodec<FriendlyByteBuf, SelectedSlotPayload> = StreamCodec.of(
            { buf, payload -> buf.writeInt(payload.selectedSlot) },
            { buf -> SelectedSlotPayload(buf.readInt()) }
        )
    }

    override fun type(): CustomPacketPayload.Type<SelectedSlotPayload> = ID
}

object AeonisNetworking {
    
    // Server-side storage of controlled entities
    private val controlledEntities = mutableMapOf<java.util.UUID, Int>()
    // Latest movement input per controlling player (used for smoother server-side mob sync/animation)
    private val latestControlInputs = mutableMapOf<java.util.UUID, MobControlPayload>()
    
    // Track attack cooldown to prevent spam
    private val attackCooldowns = mutableMapOf<java.util.UUID, Long>()

    // Additional cooldown maps (ported from QCmod)
    private val witchPotionCooldowns = mutableMapOf<java.util.UUID, Long>()
    private val teleportCooldowns = mutableMapOf<java.util.UUID, Long>()
    private val projectileCooldowns = mutableMapOf<java.util.UUID, Long>()

    // Helper accessors for cooldowns
    fun canUseTeleport(playerUuid: java.util.UUID, now: Long = System.currentTimeMillis()): Boolean {
        val next = teleportCooldowns[playerUuid] ?: 0L
        return now >= next
    }

    fun setTeleportCooldown(playerUuid: java.util.UUID, millisFromNow: Long) {
        teleportCooldowns[playerUuid] = System.currentTimeMillis() + millisFromNow
    }

    fun canUseWitchPotion(playerUuid: java.util.UUID, now: Long = System.currentTimeMillis()): Boolean {
        val next = witchPotionCooldowns[playerUuid] ?: 0L
        return now >= next
    }

    fun setWitchPotionCooldown(playerUuid: java.util.UUID, millisFromNow: Long) {
        witchPotionCooldowns[playerUuid] = System.currentTimeMillis() + millisFromNow
    }

    fun canUseProjectile(playerUuid: java.util.UUID, now: Long = System.currentTimeMillis()): Boolean {
        val next = projectileCooldowns[playerUuid] ?: 0L
        return now >= next
    }

    fun setProjectileCooldown(playerUuid: java.util.UUID, millisFromNow: Long) {
        projectileCooldowns[playerUuid] = System.currentTimeMillis() + millisFromNow
    }
    
    // Sync payload to notify clients about which players are currently controlling mobs
    data class SyncControllingPlayersPayload(val playerUUIDs: List<java.util.UUID>) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<SyncControllingPlayersPayload>(
                Identifier.fromNamespaceAndPath("aeonis-manager", "sync_controlling_players")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, SyncControllingPlayersPayload> = StreamCodec.of(
                { buf, payload ->
                    buf.writeInt(payload.playerUUIDs.size)
                    for (u in payload.playerUUIDs) {
                        buf.writeLong(u.mostSignificantBits)
                        buf.writeLong(u.leastSignificantBits)
                    }
                },
                { buf ->
                    val n = buf.readInt()
                    val list = mutableListOf<java.util.UUID>()
                    for (i in 0 until n) {
                        val msb = buf.readLong()
                        val lsb = buf.readLong()
                        list.add(java.util.UUID(msb, lsb))
                    }
                    SyncControllingPlayersPayload(list)
                }
            )
        }
        override fun type(): CustomPacketPayload.Type<SyncControllingPlayersPayload> = ID
    }

    // Server -> Client payload: controlled entity status (health + name)
    data class ControlledEntityStatusPayload(val entityId: Int, val health: Float, val maxHealth: Float, val name: String) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<ControlledEntityStatusPayload>(
                Identifier.fromNamespaceAndPath("aeonis-manager", "controlled_entity_status")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, ControlledEntityStatusPayload> = StreamCodec.of(
                { buf, payload ->
                    buf.writeInt(payload.entityId)
                    buf.writeFloat(payload.health)
                    buf.writeFloat(payload.maxHealth)
                    buf.writeUtf(payload.name)
                },
                { buf -> ControlledEntityStatusPayload(buf.readInt(), buf.readFloat(), buf.readFloat(), buf.readUtf(32767)) }
            )
        }

        override fun type(): CustomPacketPayload.Type<ControlledEntityStatusPayload> = ID
    }
    
    // Server -> Client payload: soul mode status
    data class SoulModePayload(val enabled: Boolean) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<SoulModePayload>(
                Identifier.fromNamespaceAndPath("aeonis-manager", "soul_mode")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, SoulModePayload> = StreamCodec.of(
                { buf, payload -> buf.writeBoolean(payload.enabled) },
                { buf -> SoulModePayload(buf.readBoolean()) }
            )
        }
        override fun type(): CustomPacketPayload.Type<SoulModePayload> = ID
    }
    
    // Client -> Server payload: possess existing mob (from soul mode)
    data class SoulPossessPayload(val mobId: Int) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<SoulPossessPayload>(
                Identifier.fromNamespaceAndPath("aeonis-manager", "soul_possess")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, SoulPossessPayload> = StreamCodec.of(
                { buf, payload -> buf.writeInt(payload.mobId) },
                { buf -> SoulPossessPayload(buf.readInt()) }
            )
        }
        override fun type(): CustomPacketPayload.Type<SoulPossessPayload> = ID
    }
    
    // Server -> Client payload: open manhunt setup GUI
    class OpenManhuntGuiPayload : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<OpenManhuntGuiPayload>(
                Identifier.fromNamespaceAndPath("aeonis-manager", "open_manhunt_gui")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, OpenManhuntGuiPayload> = StreamCodec.of(
                { _, _ -> },
                { _ -> OpenManhuntGuiPayload() }
            )
        }
        override fun type(): CustomPacketPayload.Type<OpenManhuntGuiPayload> = ID
    }

    // Register server-side networking handlers and periodic status updates
    fun registerServer() {
        // Register the server->client payload types
        PayloadTypeRegistry.playS2C().register(ControlledEntityStatusPayload.ID, ControlledEntityStatusPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(ControlModePayload.ID, ControlModePayload.CODEC)
        PayloadTypeRegistry.playS2C().register(SyncControllingPlayersPayload.ID, SyncControllingPlayersPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(SoulModePayload.ID, SoulModePayload.CODEC)
        PayloadTypeRegistry.playS2C().register(OpenManhuntGuiPayload.ID, OpenManhuntGuiPayload.CODEC)
        
        // Register client->server payload types
        PayloadTypeRegistry.playC2S().register(SelectedSlotPayload.ID, SelectedSlotPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(PossessPayload.ID, PossessPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(ReleasePayload.ID, ReleasePayload.CODEC)
        PayloadTypeRegistry.playC2S().register(AbilityPayload.ID, AbilityPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(MobControlPayload.ID, MobControlPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(SoulPossessPayload.ID, SoulPossessPayload.CODEC)
        
        // Register client->server selected-slot payload handler
        ServerPlayNetworking.registerGlobalReceiver(SelectedSlotPayload.ID) { payload, context ->
            val player = context.player()
            context.server().execute {
                try { player.inventory.selectedSlot = payload.selectedSlot } catch (_: Exception) {}
            }
        }

        // Send controlled entity status to each controlling player at end of server tick
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register { server ->
            for ((playerUuid, entityId) in controlledEntities) {
                try {
                    val player = server.playerList.getPlayer(playerUuid) ?: continue
                    val level = player.level() as? net.minecraft.server.level.ServerLevel ?: continue
                    val entity = level.getEntity(entityId) ?: continue
                    if (entity is net.minecraft.world.entity.LivingEntity) {
                        val health = entity.health
                        val maxHealth = entity.maxHealth
                        val name = entity.name.string
                        val payload = ControlledEntityStatusPayload(entityId, health, maxHealth, name)
                        ServerPlayNetworking.send(player, payload)
                    }
                } catch (_: Exception) {
                    // ignore
                }
            }
        }

        // Handle possess/release abilities
        ServerPlayNetworking.registerGlobalReceiver(PossessPayload.ID) { payload, context ->
            val player = context.player()
            context.server().execute {
                val level = player.level()
                val entity = level.getEntity(payload.mobId)
                if (entity is LivingEntity) {
                    // Pass server to allow broadcasting updates
                    AeonisPossession.handlePossess(player, entity, context.server())
                    // Broadcast controlling players
                    broadcastControllingPlayers(context.server())
                }
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(ReleasePayload.ID) { payload, context ->
            val player = context.player()
            context.server().execute {
                if (AeonisPossession.isActivelyPossessing(player.uuid)) {
                    AeonisPossession.handleRelease(player, payload.bodyX, payload.bodyY, payload.bodyZ)
                } else if (AeonisNetworking.isPlayerTransformed(player.uuid)) {
                    AeonisCommands.autoUntransform(player, showMessage = true)
                }
                broadcastControllingPlayers(context.server())
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(AbilityPayload.ID) { payload, context ->
            val player = context.player()
            context.server().execute {
                val entityId = controlledEntities[player.uuid] ?: return@execute
                val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return@execute
                val entity = level.getEntity(entityId)
                if (entity != null) {
                    try {
                        useSpecialAbility(player, entity)
                    } catch (e: Exception) {
                        player.sendSystemMessage(Component.literal("§7[AEONIS] Ability error: ${e.message}"))
                    }
                }
            }
        }

        // Handle mob control packets (movement, attack, etc.)
        ServerPlayNetworking.registerGlobalReceiver(MobControlPayload.ID) { payload, context ->
            val player = context.player()
            context.server().execute {
                handleMobControl(player, payload)
            }
        }
        
        // Handle soul possess packet (possess existing mob from soul mode)
        ServerPlayNetworking.registerGlobalReceiver(SoulPossessPayload.ID) { payload, context ->
            val player = context.player()
            context.server().execute {
                // Import AeonisCommands for soul mode functions
                val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return@execute
                val mob = level.getEntity(payload.mobId) as? Mob ?: run {
                    player.sendSystemMessage(Component.literal("§c[Aeonis] Invalid mob!"))
                    return@execute
                }
                
                // Check if player is in soul mode
                if (!com.qc.aeonis.command.AeonisCommands.isInSoulMode(player.uuid)) {
                    player.sendSystemMessage(Component.literal("§c[Aeonis] You must be in soul mode to possess mobs!"))
                    return@execute
                }
                
                // Possess the mob
                com.qc.aeonis.command.AeonisCommands.possessExistingMob(player, mob)
                
                // Broadcast controlling players
                broadcastControllingPlayers(context.server())
            }
        }
    }
    
    /**
     * Check if a player is currently transformed
     */
    fun isPlayerTransformed(playerUuid: java.util.UUID): Boolean {
        return controlledEntities.containsKey(playerUuid)
    }
    
    fun setControlledEntity(player: ServerPlayer, entityId: Int, selectedSlot: Int = -1) {
        controlledEntities[player.uuid] = entityId
        // Apply selected hotbar slot on server-side immediately if provided
        if (selectedSlot >= 0) {
            try { player.inventory.selectedSlot = selectedSlot } catch (_: Exception) {}
        }
        // Tell client to start controlling and inform about selected slot
        ServerPlayNetworking.send(player, ControlModePayload(true, entityId, selectedSlot))
    }
    
    fun removeControlledEntity(player: ServerPlayer) {
        controlledEntities.remove(player.uuid)
        latestControlInputs.remove(player.uuid)
        attackCooldowns.remove(player.uuid)
        try { player.isInvisible = false } catch (_: Exception) {}
        // Tell client to stop controlling
        ServerPlayNetworking.send(player, ControlModePayload(false, -1, -1))
    }
    
    fun getControlledEntityId(playerUuid: java.util.UUID): Int? {
        return controlledEntities[playerUuid]
    }
    
    fun getControlledEntitiesMap(): Map<java.util.UUID, Int> {
        return controlledEntities.toMap()
    }

    fun getLatestControlInput(playerUuid: java.util.UUID): MobControlPayload? = latestControlInputs[playerUuid]

    fun broadcastControllingPlayers(server: net.minecraft.server.MinecraftServer) {
        val uuids = controlledEntities.keys.toList()
        val payload = SyncControllingPlayersPayload(uuids)
        for (player in server.playerList.players) {
            ServerPlayNetworking.send(player, payload)
        }
    }
    
    /**
     * Send soul mode status to a player
     */
    fun sendSoulModeStatus(player: ServerPlayer, enabled: Boolean) {
        ServerPlayNetworking.send(player, SoulModePayload(enabled))
    }
    
    /**
     * Send packet to open Manhunt setup GUI on client
     */
    fun sendOpenManhuntGuiPacket(player: ServerPlayer) {
        ServerPlayNetworking.send(player, OpenManhuntGuiPayload())
    }
    
    private fun handleMobControl(player: ServerPlayer, payload: MobControlPayload) {
        val entityId = controlledEntities[player.uuid] ?: return
        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return
        val entity = level.getEntity(entityId) ?: return
        latestControlInputs[player.uuid] = payload
        
        // Apply rotation to entity (synced from player's camera)
        entity.yRot = payload.yaw
        entity.xRot = payload.pitch
        entity.setYHeadRot(payload.yaw)
        if (entity is LivingEntity) {
            entity.yBodyRot = payload.yaw
        }

        // Handle attack input -> perform MELEE attack (left-click)
        if (payload.attack) {
            // Small guard against spam using attackCooldowns
            val now = System.currentTimeMillis()
            val last = attackCooldowns[player.uuid] ?: 0L
            if (now - last >= 250L) { // 250ms cooldown between attacks
                attackCooldowns[player.uuid] = now
                try {
                    // Perform melee attack: damage entity player is looking at
                    performMeleeAttack(player, entity)
                } catch (_: Exception) {
                    // fallback: do nothing
                }
            }
        }

        // Teleport input: REMOVED - no longer used for special abilities
        // Special abilities are now triggered by the R key (AbilityPayload)
        
        // NOTE: Movement is now handled by the player's normal movement
        // The server tick in AeonisManager syncs the mob position to the player
        // This simplifies the system and makes movement feel natural
        
        // NOTE: Removed legacy action bar health display since vanilla health bar now shows mob health
        // The player's max health is synced to the mob's max health in AeonisManager tick
    }

    /**
     * Perform melee attack with the mob (triggered by short left-click)
     * Attacks originate from the MOB's position using the MOB's look direction
     */
    private fun performMeleeAttack(player: ServerPlayer, mobEntity: Entity) {
        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return
        
        if (mobEntity !is Mob) return
        val controlledMob = mobEntity
        
        // Calculate mob's melee reach based on its size
        val mobReach = getMobMeleeReach(controlledMob)
        
        // Find target entity from MOB's position using MOB's look direction
        val mobEyePos = controlledMob.eyePosition
        val lookVec = controlledMob.getViewVector(1.0f)
        val endPos = mobEyePos.add(lookVec.scale(mobReach))
        
        // Find entities in the attack path from the MOB
        val searchBox = controlledMob.boundingBox.expandTowards(lookVec.scale(mobReach)).inflate(1.0)
        val nearbyEntities = level.getEntities(controlledMob, searchBox) { e ->
            e != controlledMob && e != player && e is LivingEntity && e.isAlive
        }
        
        // Find the closest entity in the mob's line of sight
        var target: LivingEntity? = null
        var closestDistSq = mobReach * mobReach
        
        for (e in nearbyEntities) {
            val box = e.boundingBox.inflate(e.pickRadius.toDouble())
            val hitOpt = box.clip(mobEyePos, endPos)
            if (hitOpt.isPresent) {
                val distSq = mobEyePos.distanceToSqr(hitOpt.get())
                if (distSq < closestDistSq) {
                    closestDistSq = distSq
                    target = e as? LivingEntity
                }
            }
        }
        
        if (target == null) {
            // Play miss sound
            controlledMob.playSound(net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_NODAMAGE, 1.0f, 1.0f)
            return
        }
        
        // Get base mob damage from attributes (vanilla mob damage)
        var damage: Float
        try {
            damage = controlledMob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).toFloat()
        } catch (_: Exception) {
            // Fallback damage based on mob type
            damage = when (controlledMob) {
                is Zombie -> 3.0f
                is Skeleton -> 2.5f
                is net.minecraft.world.entity.boss.wither.WitherBoss -> 8.0f
                is net.minecraft.world.entity.boss.enderdragon.EnderDragon -> 10.0f
                is IronGolem -> 15.0f
                is Wolf -> 4.0f
                else -> 2.0f
            }
        }
        
        // Reset target invulnerability to allow hit
        target.invulnerableTime = 0
        target.hurtTime = 0
        
        // Deal damage from the controlled mob
        val damageSource = level.damageSources().mobAttack(controlledMob)
        target.hurt(damageSource, damage)
        
        // Play attack sound
        level.playSound(
            null,
            controlledMob.x, controlledMob.y, controlledMob.z,
            net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG,
            net.minecraft.sounds.SoundSource.PLAYERS,
            1.0f, 1.0f
        )
        
        // Make target angry at the controlled mob
        if (target is Mob) {
            target.target = controlledMob
        }
        target.lastHurtByMob = controlledMob
    }
    
    /**
     * Get the melee attack reach for a mob based on its type and size
     */
    private fun getMobMeleeReach(mob: Mob): Double {
        // Special cases for specific mobs
        return when {
            mob is IronGolem -> 4.0
            mob is net.minecraft.world.entity.monster.Ravager -> 4.5
            mob.type == net.minecraft.world.entity.EntityType.WARDEN -> 5.0
            mob.type == net.minecraft.world.entity.EntityType.ENDER_DRAGON -> 8.0
            mob is net.minecraft.world.entity.monster.Ghast -> 6.0
            mob.type == net.minecraft.world.entity.EntityType.GIANT -> 8.0
            mob.type == net.minecraft.world.entity.EntityType.WITHER -> 5.0
            mob is Bee -> 1.5
            else -> {
                // Default: base reach on mob's bounding box size
                val width = mob.bbWidth.toDouble()
                val height = mob.bbHeight.toDouble()
                val baseReach = maxOf(width, height) + 1.5
                // Clamp between reasonable values
                baseReach.coerceIn(2.0, 6.0)
            }
        }
    }
    
    // Mob-specific special abilities triggered by player while controlling (R key)
    private fun useSpecialAbility(player: ServerPlayer, mobEntity: Entity) {
        val now = System.currentTimeMillis()
        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return
        try {
            when {
                // Enderman: Teleport to where player is looking
                mobEntity is EnderMan -> {
                    if (!canUseTeleport(player.uuid, now)) return
                    setTeleportCooldown(player.uuid, 200L)
                    val hit = player.pick(32.0, 0f, false)
                    if (hit is net.minecraft.world.phys.BlockHitResult) {
                        val pos = hit.blockPos
                        val face = hit.direction
                        val x = pos.x + 0.5 + face.stepX
                        val y = pos.y.toDouble() + face.stepY
                        val z = pos.z + 0.5 + face.stepZ
                        mobEntity.teleportTo(x, y, z)
                        level.playSound(null, mobEntity.x, mobEntity.y, mobEntity.z, 
                            net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, 
                            net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 1.0f)
                    }
                }
                // Wither: Shoot wither skull
                mobEntity is WitherBoss -> {
                    if (!canUseProjectile(player.uuid, now)) return
                    setProjectileCooldown(player.uuid, 100L)
                    val look = mobEntity.getViewVector(1.0f)
                    val skull = WitherSkull(level, mobEntity, Vec3(look.x, look.y, look.z))
                    skull.setPos(mobEntity.x, mobEntity.eyeY, mobEntity.z)
                    skull.setDeltaMovement(look.x * 0.5, look.y * 0.5, look.z * 0.5)
                    level.addFreshEntity(skull)
                    level.levelEvent(null, 1024, mobEntity.blockPosition(), 0)
                }
                // Blaze: Shoot small fireball
                mobEntity is Blaze -> {
                    if (!canUseProjectile(player.uuid, now)) return
                    setProjectileCooldown(player.uuid, 100L)
                    val look = mobEntity.getViewVector(1.0f)
                    val fb = SmallFireball(level, mobEntity, Vec3(look.x, look.y, look.z))
                    fb.setPos(mobEntity.x, mobEntity.eyeY, mobEntity.z)
                    level.addFreshEntity(fb)
                    level.playSound(null, mobEntity.x, mobEntity.y, mobEntity.z, 
                        net.minecraft.sounds.SoundEvents.BLAZE_SHOOT, 
                        net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 1.0f)
                }
                // Ghast: Shoot large fireball with charging animation
                mobEntity is Ghast -> {
                    if (!canUseProjectile(player.uuid, now)) return
                    setProjectileCooldown(player.uuid, 1100L)
                    mobEntity.playSound(net.minecraft.sounds.SoundEvents.GHAST_WARN, 1.0f, 1.0f)
                    val look = mobEntity.getViewVector(1.0f)
                    val fb = LargeFireball(level, mobEntity, Vec3(look.x, look.y, look.z), 1)
                    fb.setPos(mobEntity.x, mobEntity.eyeY, mobEntity.z)
                    level.addFreshEntity(fb)
                    level.levelEvent(null, 1018, mobEntity.blockPosition(), 0)
                }
                // Snow Golem: Throw snowball
                mobEntity is SnowGolem -> {
                    if (!canUseProjectile(player.uuid, now)) return
                    setProjectileCooldown(player.uuid, 100L)
                    val look = mobEntity.getViewVector(1.0f)
                    val snowball = Snowball(net.minecraft.world.entity.EntityType.SNOWBALL, level)
                    snowball.owner = mobEntity
                    snowball.setPos(mobEntity.x, mobEntity.eyeY, mobEntity.z)
                    snowball.shoot(look.x, look.y, look.z, 1.6f, 1.0f)
                    level.addFreshEntity(snowball)
                    level.playSound(null, mobEntity.x, mobEntity.y, mobEntity.z, 
                        net.minecraft.sounds.SoundEvents.SNOWBALL_THROW, 
                        net.minecraft.sounds.SoundSource.NEUTRAL, 0.5f, 0.4f / (level.random.nextFloat() * 0.4f + 0.8f))
                }
                // Creeper: Ignite explosion
                mobEntity is Creeper -> {
                    mobEntity.ignite()
                }
                // Witch: Apply potion effect in a cone/area in front
                mobEntity is Witch -> {
                    if (!canUseWitchPotion(player.uuid, now)) return
                    setWitchPotionCooldown(player.uuid, 1000L)
                    // Random potion effect
                    val random = level.random.nextInt(4)
                    val effect = when (random) {
                        0 -> net.minecraft.world.effect.MobEffects.INSTANT_DAMAGE
                        1 -> net.minecraft.world.effect.MobEffects.POISON
                        2 -> net.minecraft.world.effect.MobEffects.SLOWNESS
                        else -> net.minecraft.world.effect.MobEffects.WEAKNESS
                    }
                    // Get target position
                    val look = mobEntity.getViewVector(1.0f)
                    val targetPos = Vec3(
                        mobEntity.x + look.x * 8.0,
                        mobEntity.eyeY + look.y * 8.0,
                        mobEntity.z + look.z * 8.0
                    )
                    // Apply effect to all entities in splash radius at target
                    val splashBox = AABB(targetPos.x - 4.0, targetPos.y - 2.0, targetPos.z - 4.0,
                                         targetPos.x + 4.0, targetPos.y + 2.0, targetPos.z + 4.0)
                    for (entity in level.getEntitiesOfClass(LivingEntity::class.java, splashBox)) {
                        if (entity != mobEntity && entity != player) {
                            entity.addEffect(net.minecraft.world.effect.MobEffectInstance(effect, 200, 0))
                        }
                    }
                    // Spawn particles at target
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH, 
                        targetPos.x, targetPos.y, targetPos.z, 30, 1.0, 0.5, 1.0, 0.1)
                    level.playSound(null, mobEntity.x, mobEntity.y, mobEntity.z, 
                        net.minecraft.sounds.SoundEvents.WITCH_THROW, 
                        net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 1.0f)
                    level.playSound(null, targetPos.x, targetPos.y, targetPos.z, 
                        net.minecraft.sounds.SoundEvents.SPLASH_POTION_BREAK, 
                        net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 1.0f)
                }
                // Evoker: Summon fangs line + vex
                mobEntity is Evoker -> {
                    if (!canUseProjectile(player.uuid, now)) return
                    setProjectileCooldown(player.uuid, 500L)
                    val look = mobEntity.getViewVector(1.0f)
                    // Spawn line of evoker fangs
                    for (i in 1..5) {
                        val x = mobEntity.x + look.x * i * 1.5
                        val z = mobEntity.z + look.z * i * 1.5
                        val y = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 
                            net.minecraft.core.BlockPos(x.toInt(), 0, z.toInt())).y
                        val fangs = EvokerFangs(level, x, y.toDouble(), z, 
                            Math.atan2(look.z, look.x).toFloat(), i * 2, mobEntity)
                        level.addFreshEntity(fangs)
                    }
                    // Summon a vex
                    val vex = Vex(net.minecraft.world.entity.EntityType.VEX, level)
                    vex.setPos(mobEntity.x, mobEntity.y + 1.0, mobEntity.z)
                    vex.setOwner(mobEntity)
                    vex.setLimitedLife(600) // 30 seconds
                    level.addFreshEntity(vex)
                    level.playSound(null, mobEntity.x, mobEntity.y, mobEntity.z, 
                        net.minecraft.sounds.SoundEvents.EVOKER_CAST_SPELL, 
                        net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 1.0f)
                }
                // Warden: Sonic boom attack
                mobEntity is Warden -> {
                    if (!canUseProjectile(player.uuid, now)) return
                    setProjectileCooldown(player.uuid, 3000L)
                    level.playSound(null, mobEntity.x, mobEntity.y, mobEntity.z, 
                        net.minecraft.sounds.SoundEvents.WARDEN_SONIC_CHARGE, 
                        net.minecraft.sounds.SoundSource.HOSTILE, 3.0f, 1.0f)
                    // Broadcast animation to clients
                    level.broadcastEntityEvent(mobEntity, 62.toByte())
                    // Delayed attack after charge-up
                    val server = level.server
                    server?.execute {
                        Thread {
                            try {
                                Thread.sleep(1700)
                                server.execute {
                                    if (!mobEntity.isAlive) return@execute
                                    level.playSound(null, mobEntity.x, mobEntity.y, mobEntity.z, 
                                        net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM, 
                                        net.minecraft.sounds.SoundSource.HOSTILE, 3.0f, 1.0f)
                                    val hit = player.pick(15.0, 0f, false)
                                    val targetPos = hit.location
                                    val startPos = Vec3(mobEntity.x, mobEntity.eyeY, mobEntity.z)
                                    val direction = targetPos.subtract(startPos).normalize()
                                    val distance = startPos.distanceTo(targetPos)
                                    // Spawn particles along the beam
                                    var d = 0.0
                                    while (d < distance) {
                                        val x = startPos.x + direction.x * d
                                        val y = startPos.y + direction.y * d
                                        val z = startPos.z + direction.z * d
                                        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SONIC_BOOM, x, y, z, 1, 0.0, 0.0, 0.0, 0.0)
                                        d += 0.5
                                    }
                                    // Damage entities in the beam
                                    val damageBox = AABB(startPos.x, startPos.y, startPos.z, 
                                        targetPos.x, targetPos.y, targetPos.z).inflate(1.0)
                                    for (entity in level.getEntities(mobEntity, damageBox)) {
                                        if (entity is LivingEntity && entity != player) {
                                            entity.hurt(level.damageSources().sonicBoom(mobEntity), 10.0f)
                                            val dx = entity.x - mobEntity.x
                                            val dz = entity.z - mobEntity.z
                                            val dist = Math.sqrt(dx * dx + dz * dz)
                                            if (dist > 0) {
                                                entity.setDeltaMovement(dx / dist * 2.5, 0.5, dz / dist * 2.5)
                                                entity.hurtMarked = true
                                            }
                                        }
                                    }
                                }
                            } catch (_: InterruptedException) {}
                        }.start()
                    }
                }
                // Breeze: Shoot wind charge
                mobEntity is Breeze -> {
                    if (!canUseProjectile(player.uuid, now)) return
                    setProjectileCooldown(player.uuid, 250L)
                    val windCharge = BreezeWindCharge(mobEntity, level)
                    windCharge.setPos(mobEntity.x, mobEntity.eyeY, mobEntity.z)
                    val look = mobEntity.getViewVector(1.0f)
                    windCharge.shoot(look.x, look.y, look.z, 1.5f, 0.0f)
                    level.addFreshEntity(windCharge)
                    level.playSound(null, mobEntity.x, mobEntity.y, mobEntity.z, 
                        net.minecraft.sounds.SoundEvents.BREEZE_SHOOT, 
                        net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 1.0f)
                }
                // Skeleton and variants: Shoot arrow
                mobEntity is AbstractSkeleton -> {
                    if (!canUseProjectile(player.uuid, now)) return
                    setProjectileCooldown(player.uuid, 200L)
                    val arrow = Arrow(level, mobEntity, ItemStack(Items.ARROW), null)
                    val look = mobEntity.getViewVector(1.0f)
                    arrow.setPos(mobEntity.x, mobEntity.eyeY, mobEntity.z)
                    arrow.shoot(look.x, look.y, look.z, 1.6f, 1.0f)
                    level.addFreshEntity(arrow)
                    level.playSound(null, mobEntity.x, mobEntity.y, mobEntity.z, 
                        net.minecraft.sounds.SoundEvents.SKELETON_SHOOT, 
                        net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 1.0f / (level.random.nextFloat() * 0.4f + 0.8f))
                }
                // Pillager: Shoot arrow (crossbow)
                mobEntity is Pillager -> {
                    if (!canUseProjectile(player.uuid, now)) return
                    setProjectileCooldown(player.uuid, 200L)
                    val arrow = Arrow(level, mobEntity, ItemStack(Items.ARROW), null)
                    val look = mobEntity.getViewVector(1.0f)
                    arrow.setPos(mobEntity.x, mobEntity.eyeY, mobEntity.z)
                    arrow.shoot(look.x, look.y, look.z, 1.6f, 1.0f)
                    level.addFreshEntity(arrow)
                    level.playSound(null, mobEntity.x, mobEntity.y, mobEntity.z, 
                        net.minecraft.sounds.SoundEvents.CROSSBOW_SHOOT, 
                        net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 1.0f / (level.random.nextFloat() * 0.4f + 0.8f))
                }
                // Default: No special ability
                else -> {
                    // No special ability for this mob type
                }
            }
        } catch (e: Exception) {
            // Log error but don't crash
            player.sendSystemMessage(Component.literal("§7[AEONIS] Ability failed: ${e.message}"))
        }
    }
    
    private fun sendEntityStatus(player: ServerPlayer, entity: Entity) {
        if (entity is LivingEntity) {
            val health = entity.health.toInt()
            val maxHealth = entity.maxHealth.toInt()
            val healthBar = buildHealthBar(health, maxHealth)
            
            // Get held item info
            val mainHand = entity.getItemBySlot(EquipmentSlot.MAINHAND)
            val offHand = entity.getItemBySlot(EquipmentSlot.OFFHAND)
            
            val heldInfo = buildHeldItemInfo(mainHand, offHand)
            
            // Send as action bar
            val message = Component.literal("§c❤ $healthBar §7($health/$maxHealth) $heldInfo")
            player.displayClientMessage(message, true)
        }
    }
    
    private fun buildHealthBar(health: Int, maxHealth: Int): String {
        val filledBars = ((health.toFloat() / maxHealth.toFloat()) * 10).toInt()
        val emptyBars = 10 - filledBars
        return "§c" + "█".repeat(filledBars) + "§8" + "█".repeat(emptyBars)
    }
    
    private fun buildHeldItemInfo(mainHand: ItemStack, offHand: ItemStack): String {
        val parts = mutableListOf<String>()
        
        if (!mainHand.isEmpty) {
            parts.add("§f⚔ ${mainHand.hoverName.string}")
        }
        if (!offHand.isEmpty) {
            parts.add("§f🛡 ${offHand.hoverName.string}")
        }
        
        return if (parts.isEmpty()) "" else "§7| ${parts.joinToString(" ")}"
    }
    
    private fun canAttack(entity: Entity): Boolean {
        // Check if this entity type can attack
        return entity is Monster || 
               entity is Wolf ||
               entity is IronGolem ||
               entity is Mob // Most mobs can attack
    }
    
    private fun handleMobAttack(player: ServerPlayer, entity: Entity, yaw: Float, pitch: Float) {
        // Check cooldown (500ms between attacks)
        val now = System.currentTimeMillis()
        val lastAttack = attackCooldowns[player.uuid] ?: 0L
        if (now - lastAttack < 500) return
        attackCooldowns[player.uuid] = now
        
        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return
        
        // Calculate look direction
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        
        val lookX = -Math.sin(yawRad) * Math.cos(pitchRad)
        val lookY = -Math.sin(pitchRad)
        val lookZ = Math.cos(yawRad) * Math.cos(pitchRad)
        val lookVec = Vec3(lookX, lookY, lookZ).normalize()
        
        // Get attack reach based on entity size
        val reach = if (entity is LivingEntity) {
            entity.bbWidth.toDouble().coerceAtLeast(3.0) + 2.0
        } else {
            4.0
        }
        
        // Create attack box in front of entity
        val attackStart = entity.position().add(0.0, entity.bbHeight * 0.5, 0.0)
        val attackEnd = attackStart.add(lookVec.scale(reach))
        
        val attackBox = AABB(
            minOf(attackStart.x, attackEnd.x) - 1.0,
            minOf(attackStart.y, attackEnd.y) - 1.0,
            minOf(attackStart.z, attackEnd.z) - 1.0,
            maxOf(attackStart.x, attackEnd.x) + 1.0,
            maxOf(attackStart.y, attackEnd.y) + 1.0,
            maxOf(attackStart.z, attackEnd.z) + 1.0
        )
        
        // Find entities in attack range
        val targets = level.getEntitiesOfClass(
            LivingEntity::class.java,
            attackBox
        ) { target ->
            target != entity && 
            target != player && 
            target.isAlive &&
            !target.isSpectator
        }
        
        // Attack the closest target in the direction we're looking
        var closestTarget: LivingEntity? = null
        var closestDist = Double.MAX_VALUE
        
        for (target in targets) {
            val toTarget = target.position().subtract(entity.position()).normalize()
            val dot = lookVec.dot(toTarget)
            
            // Only attack if target is roughly in front (dot product > 0.5 means within ~60 degrees)
            if (dot > 0.3) {
                val dist = entity.distanceToSqr(target)
                if (dist < closestDist) {
                    closestDist = dist
                    closestTarget = target
                }
            }
        }
        
        // Perform the attack
        closestTarget?.let { target ->
            if (entity is Mob) {
                // Use the mob's actual attack
                entity.setTarget(target)
                entity.doHurtTarget(level, target)
                entity.setTarget(null)
                
                // Play attack sound
                entity.playAmbientSound()
            } else if (entity is LivingEntity) {
                // Generic attack for other living entities
                target.hurt(level.damageSources().mobAttack(entity), 4.0f)
            }
            
            // Swing arm animation
            if (entity is LivingEntity) {
                entity.swing(net.minecraft.world.InteractionHand.MAIN_HAND)
            }
        }
    }
    
    /**
     * Check if an entity is a flying mob that needs 3D flight controls
     * Based on QCmod canMobFly() function
     * Public so AeonisManager tick handler can enable player flight for flying mobs
     */
    fun canMobFly(entity: Entity): Boolean {
        return entity is Ghast ||
               isGhastLike(entity) ||
               entity is Phantom ||
               entity is WitherBoss ||
               entity is EnderDragon ||
               entity is Allay ||
               entity is Bee ||
               entity is Parrot ||
               entity is Vex ||
               entity is Blaze ||
               entity is Breeze ||
               entity.type == net.minecraft.world.entity.EntityType.BAT
    }
    
    // Internal alias for backward compatibility
    private fun isFlightMob(entity: Entity): Boolean = canMobFly(entity)

    private fun isGhastLike(entity: Entity): Boolean {
        return try {
            val path = entity.type.builtInRegistryHolder().key().identifier().path
            path.contains("ghast", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Handle Wither skull attack
     */
    private fun handleWitherAttack(player: ServerPlayer, wither: WitherBoss, yaw: Float, pitch: Float) {
        // Check cooldown (300ms between skull shots - faster than melee)
        val now = System.currentTimeMillis()
        val lastAttack = attackCooldowns[player.uuid] ?: 0L
        if (now - lastAttack < 300) return
        attackCooldowns[player.uuid] = now
        
        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return
        
        // Calculate look direction
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        
        val lookX = -Math.sin(yawRad) * Math.cos(pitchRad)
        val lookY = -Math.sin(pitchRad)
        val lookZ = Math.cos(yawRad) * Math.cos(pitchRad)
        
        // Spawn position - from wither's head
        val spawnX = wither.x + lookX * 2.0
        val spawnY = wither.y + 3.0 + lookY * 2.0
        val spawnZ = wither.z + lookZ * 2.0
        
        // Create wither skull
        val skull = WitherSkull(level, wither, Vec3(lookX, lookY, lookZ).normalize())
        skull.setPos(spawnX, spawnY, spawnZ)
        skull.setOwner(wither)
        
        // Set power (velocity)
        val power = 1.5
        skull.setDeltaMovement(lookX * power, lookY * power, lookZ * power)
        
        // Spawn the skull
        level.addFreshEntity(skull)
        
        // Play wither shoot sound
        level.playSound(
            null,
            wither.x, wither.y, wither.z,
            net.minecraft.sounds.SoundEvents.WITHER_SHOOT,
            net.minecraft.sounds.SoundSource.HOSTILE,
            1.0f,
            1.0f
        )
    }
    
    /**
     * Handle Ender Dragon fireball attack
     */
    private fun handleDragonAttack(player: ServerPlayer, dragon: EnderDragon, yaw: Float, pitch: Float) {
        // Check cooldown (400ms between fireballs)
        val now = System.currentTimeMillis()
        val lastAttack = attackCooldowns[player.uuid] ?: 0L
        if (now - lastAttack < 400) return
        attackCooldowns[player.uuid] = now
        
        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return
        
        // Calculate look direction
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        
        val lookX = -Math.sin(yawRad) * Math.cos(pitchRad)
        val lookY = -Math.sin(pitchRad)
        val lookZ = Math.cos(yawRad) * Math.cos(pitchRad)
        
        // Spawn position - from dragon's head
        val spawnX = dragon.x + lookX * 4.0
        val spawnY = dragon.y + 3.0 + lookY * 4.0
        val spawnZ = dragon.z + lookZ * 4.0
        
        // Create dragon fireball
        val fireball = DragonFireball(level, dragon, Vec3(lookX, lookY, lookZ).normalize())
        fireball.setPos(spawnX, spawnY, spawnZ)
        
        // Set velocity
        val power = 1.2
        fireball.setDeltaMovement(lookX * power, lookY * power, lookZ * power)
        
        // Spawn the fireball
        level.addFreshEntity(fireball)
        
        // Play dragon shoot sound
        level.playSound(
            null,
            dragon.x, dragon.y, dragon.z,
            net.minecraft.sounds.SoundEvents.ENDER_DRAGON_SHOOT,
            net.minecraft.sounds.SoundSource.HOSTILE,
            1.0f,
            1.0f
        )
    }
    
    /**
     * Handle Breeze wind charge attack
     */
    private fun handleBreezeAttack(player: ServerPlayer, breeze: Breeze, yaw: Float, pitch: Float) {
        // Check cooldown (350ms between wind charges - fast and fun!)
        val now = System.currentTimeMillis()
        val lastAttack = attackCooldowns[player.uuid] ?: 0L
        if (now - lastAttack < 350) return
        attackCooldowns[player.uuid] = now
        
        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return
        
        // Calculate look direction
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        
        val lookX = -Math.sin(yawRad) * Math.cos(pitchRad)
        val lookY = -Math.sin(pitchRad)
        val lookZ = Math.cos(yawRad) * Math.cos(pitchRad)
        
        // Spawn position - from breeze's center
        val spawnX = breeze.x + lookX * 1.5
        val spawnY = breeze.y + 1.0 + lookY * 1.5
        val spawnZ = breeze.z + lookZ * 1.5
        
        // Create breeze wind charge
        val windCharge = BreezeWindCharge(breeze, level)
        windCharge.setPos(spawnX, spawnY, spawnZ)
        
        // Set velocity - wind charges are fast!
        val power = 1.8
        windCharge.deltaMovement = Vec3(lookX * power, lookY * power, lookZ * power)
        
        // Spawn the wind charge
        level.addFreshEntity(windCharge)
        
        // Play breeze shoot sound
        level.playSound(
            null,
            breeze.x, breeze.y, breeze.z,
            net.minecraft.sounds.SoundEvents.BREEZE_SHOOT,
            net.minecraft.sounds.SoundSource.HOSTILE,
            1.0f,
            1.0f
        )
    }
    
    /**
     * Handle Blaze fireball attack (rapid small fireballs)
     */
    private fun handleBlazeAttack(player: ServerPlayer, blaze: Blaze, yaw: Float, pitch: Float) {
        // Check cooldown (250ms between fireballs - rapid fire!)
        val now = System.currentTimeMillis()
        val lastAttack = attackCooldowns[player.uuid] ?: 0L
        if (now - lastAttack < 250) return
        attackCooldowns[player.uuid] = now
        
        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return
        
        // Calculate look direction
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        
        val lookX = -Math.sin(yawRad) * Math.cos(pitchRad)
        val lookY = -Math.sin(pitchRad)
        val lookZ = Math.cos(yawRad) * Math.cos(pitchRad)
        
        // Spawn position - from blaze's center
        val spawnX = blaze.x + lookX * 1.5
        val spawnY = blaze.y + 1.0 + lookY * 1.5
        val spawnZ = blaze.z + lookZ * 1.5
        
        // Create small fireball (like blaze shoots)
        val fireball = SmallFireball(level, blaze, Vec3(lookX, lookY, lookZ).normalize())
        fireball.setPos(spawnX, spawnY, spawnZ)
        
        // Set velocity
        val power = 1.5
        fireball.setDeltaMovement(lookX * power, lookY * power, lookZ * power)
        
        // Spawn the fireball
        level.addFreshEntity(fireball)
        
        // Play blaze shoot sound
        level.playSound(
            null,
            blaze.x, blaze.y, blaze.z,
            net.minecraft.sounds.SoundEvents.BLAZE_SHOOT,
            net.minecraft.sounds.SoundSource.HOSTILE,
            1.0f,
            1.0f
        )
    }
    
    /**
     * Handle Ghast fireball attack (large explosive fireballs)
     */
    private fun handleGhastAttack(player: ServerPlayer, ghast: Ghast, yaw: Float, pitch: Float) {
        // Check cooldown (600ms between fireballs - big and powerful!)
        val now = System.currentTimeMillis()
        val lastAttack = attackCooldowns[player.uuid] ?: 0L
        if (now - lastAttack < 600) return
        attackCooldowns[player.uuid] = now
        
        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return
        
        // Calculate look direction
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        
        val lookX = -Math.sin(yawRad) * Math.cos(pitchRad)
        val lookY = -Math.sin(pitchRad)
        val lookZ = Math.cos(yawRad) * Math.cos(pitchRad)
        
        // Spawn position - from ghast's face (ghasts are big!)
        val spawnX = ghast.x + lookX * 3.0
        val spawnY = ghast.y + 2.0 + lookY * 3.0
        val spawnZ = ghast.z + lookZ * 3.0
        
        // Create large fireball (like ghast shoots) - explosive power 1
        val fireball = LargeFireball(level, ghast, Vec3(lookX, lookY, lookZ).normalize(), 1)
        fireball.setPos(spawnX, spawnY, spawnZ)
        
        // Set velocity - ghast fireballs are slower but powerful
        val power = 1.0
        fireball.setDeltaMovement(lookX * power, lookY * power, lookZ * power)
        
        // Spawn the fireball
        level.addFreshEntity(fireball)
        
        // Play ghast shoot sound (the iconic fireball sound!)
        level.playSound(
            null,
            ghast.x, ghast.y, ghast.z,
            net.minecraft.sounds.SoundEvents.GHAST_SHOOT,
            net.minecraft.sounds.SoundSource.HOSTILE,
            1.0f,
            1.0f
        )
        
        // Also play the warning sound
        level.playSound(
            null,
            ghast.x, ghast.y, ghast.z,
            net.minecraft.sounds.SoundEvents.GHAST_WARN,
            net.minecraft.sounds.SoundSource.HOSTILE,
            0.5f,
            1.0f
        )
    }

    /**
     * Handle Ghast-like custom mobs (e.g., Happy Ghast) that are not instance of Ghast
     */
    private fun handleGhastLikeAttack(player: ServerPlayer, entity: LivingEntity) {
        // Check cooldown (600ms like ghast)
        val now = System.currentTimeMillis()
        val lastAttack = attackCooldowns[player.uuid] ?: 0L
        if (now - lastAttack < 600) return
        attackCooldowns[player.uuid] = now

        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return

        // Use player-facing yaw/pitch (entity rotation already synced)
        val yaw = entity.yRot
        val pitch = entity.xRot

        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())

        val lookX = -Math.sin(yawRad) * Math.cos(pitchRad)
        val lookY = -Math.sin(pitchRad)
        val lookZ = Math.cos(yawRad) * Math.cos(pitchRad)

        // Spawn position - in front of face
        val spawnX = entity.x + lookX * 3.0
        val spawnY = entity.y + entity.eyeHeight.toDouble() + lookY * 2.0
        val spawnZ = entity.z + lookZ * 3.0

        // Create large fireball (power 1 like ghast)
        val direction = Vec3(lookX, lookY, lookZ)
        val fireball = LargeFireball(level, entity, direction, 1)
        fireball.setPos(spawnX, spawnY, spawnZ)
        fireball.setDeltaMovement(direction.normalize())

        level.addFreshEntity(fireball)

        // Play ghast shoot sounds
        level.playSound(null, entity.x, entity.y, entity.z,
            net.minecraft.sounds.SoundEvents.GHAST_SHOOT,
            net.minecraft.sounds.SoundSource.HOSTILE,
            1.0f, 1.0f)
        level.playSound(null, entity.x, entity.y, entity.z,
            net.minecraft.sounds.SoundEvents.GHAST_WARN,
            net.minecraft.sounds.SoundSource.HOSTILE,
            0.5f, 1.0f)
    }

    /**
     * Handle Chicken egg drop (fun gag)
     */
    private fun handleChickenEgg(player: ServerPlayer, chicken: Chicken) {
        // Check cooldown (700ms between eggs)
        val now = System.currentTimeMillis()
        val lastAttack = attackCooldowns[player.uuid] ?: 0L
        if (now - lastAttack < 700) return
        attackCooldowns[player.uuid] = now

        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return

        // Spawn an egg item slightly above the chicken
        val spawnPos = Vec3(chicken.x, chicken.y + 0.6, chicken.z)
        val eggStack = ItemStack(Items.EGG, 1)
        val eggEntity = ItemEntity(level, spawnPos.x, spawnPos.y, spawnPos.z, eggStack)

        // Give it a little forward toss for fun
        val facing = chicken.lookAngle.normalize().scale(0.25)
        eggEntity.deltaMovement = Vec3(facing.x, 0.15, facing.z)

        level.addFreshEntity(eggEntity)

        // Play chicken egg sound
        level.playSound(
            null,
            chicken.x, chicken.y, chicken.z,
            net.minecraft.sounds.SoundEvents.CHICKEN_EGG,
            net.minecraft.sounds.SoundSource.NEUTRAL,
            1.0f,
            1.0f
        )
    }

    /**
     * Handle Creeper explosion - explode when left click is pressed near anything
     * The creeper will die in the explosion like the real mob
     */
    private fun handleCreeperExplosion(player: ServerPlayer, creeper: Creeper, yaw: Float, pitch: Float) {
        // Check cooldown (1500ms to prevent spam - also acts as "charging" time)
        val now = System.currentTimeMillis()
        val lastAttack = attackCooldowns[player.uuid] ?: 0L
        if (now - lastAttack < 1500) return
        attackCooldowns[player.uuid] = now

        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return

        // Calculate look direction to check if there's something nearby to explode on
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())

        val lookX = -Math.sin(yawRad) * Math.cos(pitchRad)
        val lookY = -Math.sin(pitchRad)
        val lookZ = Math.cos(yawRad) * Math.cos(pitchRad)
        val lookVec = Vec3(lookX, lookY, lookZ).normalize()

        // Check if there's an entity or block within 4 blocks in front
        val checkRange = 4.0
        val startPos = creeper.position().add(0.0, creeper.bbHeight * 0.5, 0.0)
        val endPos = startPos.add(lookVec.scale(checkRange))

        // Check for nearby entities
        val nearbyEntities = level.getEntitiesOfClass(
            LivingEntity::class.java,
            AABB(
                minOf(startPos.x, endPos.x) - 2.0,
                minOf(startPos.y, endPos.y) - 2.0,
                minOf(startPos.z, endPos.z) - 2.0,
                maxOf(startPos.x, endPos.x) + 2.0,
                maxOf(startPos.y, endPos.y) + 2.0,
                maxOf(startPos.z, endPos.z) + 2.0
            )
        ) { it != creeper && it != player && it.isAlive }

        // Check for blocks in front (raycast)
        val blockHitResult = level.clip(net.minecraft.world.level.ClipContext(
            startPos,
            endPos,
            net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE,
            creeper
        ))
        val hasBlockNearby = blockHitResult.type == net.minecraft.world.phys.HitResult.Type.BLOCK

        // Only explode if there's something nearby (entity or block)
        if (nearbyEntities.isEmpty() && !hasBlockNearby) {
            player.displayClientMessage(Component.literal("§c💥 Get closer to something to explode!"), true)
            return
        }

        // Play creeper hiss sound before explosion
        level.playSound(
            null,
            creeper.x, creeper.y, creeper.z,
            net.minecraft.sounds.SoundEvents.CREEPER_PRIMED,
            net.minecraft.sounds.SoundSource.HOSTILE,
            1.0f,
            1.0f
        )

        // Determine explosion power (charged creeper = bigger explosion)
        val explosionPower = if (creeper.isPowered) 6.0f else 3.0f

        // Create the explosion at creeper's position
        level.explode(
            creeper,
            creeper.x, creeper.y + creeper.bbHeight * 0.5, creeper.z,
            explosionPower,
            net.minecraft.world.level.Level.ExplosionInteraction.MOB
        )

        // Remove the player from controlling the entity first
        removeControlledEntity(player)

        // Kill the creeper (they die when they explode)
        creeper.kill(level)

        // Teleport player to where they were spectating from
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL)

        // Notify the player
        player.displayClientMessage(Component.literal("§c💥 BOOM! Your creeper exploded!"), false)
    }

    /**
     * Handle Skeleton arrow shooting - shoot arrows using the bow in hand
     * Works for Skeleton, Stray, Wither Skeleton, and Bogged
     */
    private fun handleSkeletonArrow(player: ServerPlayer, skeleton: AbstractSkeleton, yaw: Float, pitch: Float) {
        // Check cooldown (800ms between shots - similar to skeleton attack speed)
        val now = System.currentTimeMillis()
        val lastAttack = attackCooldowns[player.uuid] ?: 0L
        if (now - lastAttack < 800) return
        attackCooldowns[player.uuid] = now

        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return

        // Calculate look direction
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())

        val lookX = -Math.sin(yawRad) * Math.cos(pitchRad)
        val lookY = -Math.sin(pitchRad)
        val lookZ = Math.cos(yawRad) * Math.cos(pitchRad)
        val lookVec = Vec3(lookX, lookY, lookZ).normalize()

        // Spawn arrow from skeleton's eye position
        val spawnPos = Vec3(skeleton.x, skeleton.eyeY - 0.1, skeleton.z)

        // Create the arrow
        val arrow = Arrow(level, skeleton, ItemStack(Items.ARROW), null)
        arrow.setPos(spawnPos.x, spawnPos.y, spawnPos.z)

        // Set arrow velocity (speed similar to skeleton's shot)
        val arrowSpeed = 1.6
        arrow.shoot(lookVec.x, lookVec.y, lookVec.z, arrowSpeed.toFloat(), 1.0f)

        // Add the arrow to the world
        level.addFreshEntity(arrow)

        // Swing arm animation
        skeleton.swing(net.minecraft.world.InteractionHand.MAIN_HAND)

        // Play bow shoot sound
        level.playSound(
            null,
            skeleton.x, skeleton.y, skeleton.z,
            net.minecraft.sounds.SoundEvents.ARROW_SHOOT,
            net.minecraft.sounds.SoundSource.HOSTILE,
            1.0f,
            1.0f / (level.random.nextFloat() * 0.4f + 0.8f)
        )
    }

    /**
     * Handle Pillager crossbow arrow shooting
     */
    private fun handlePillagerArrow(player: ServerPlayer, pillager: Pillager, yaw: Float, pitch: Float) {
        // Check cooldown (1000ms between shots - crossbow takes longer)
        val now = System.currentTimeMillis()
        val lastAttack = attackCooldowns[player.uuid] ?: 0L
        if (now - lastAttack < 1000) return
        attackCooldowns[player.uuid] = now

        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return

        // Calculate look direction
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())

        val lookX = -Math.sin(yawRad) * Math.cos(pitchRad)
        val lookY = -Math.sin(pitchRad)
        val lookZ = Math.cos(yawRad) * Math.cos(pitchRad)
        val lookVec = Vec3(lookX, lookY, lookZ).normalize()

        // Spawn arrow from pillager's eye position
        val spawnPos = Vec3(pillager.x, pillager.eyeY - 0.1, pillager.z)

        // Create the arrow (crossbow arrows are faster and more accurate)
        val arrow = Arrow(level, pillager, ItemStack(Items.ARROW), null)
        arrow.setPos(spawnPos.x, spawnPos.y, spawnPos.z)
        arrow.setCritArrow(true) // Crossbow arrows are always critical

        // Set arrow velocity (crossbow is faster than bow)
        val arrowSpeed = 3.15
        arrow.shoot(lookVec.x, lookVec.y, lookVec.z, arrowSpeed.toFloat(), 0.0f) // 0 inaccuracy for crossbow

        // Add the arrow to the world
        level.addFreshEntity(arrow)

        // Swing arm animation
        pillager.swing(net.minecraft.world.InteractionHand.MAIN_HAND)

        // Play crossbow shoot sound
        level.playSound(
            null,
            pillager.x, pillager.y, pillager.z,
            net.minecraft.sounds.SoundEvents.CROSSBOW_SHOOT,
            net.minecraft.sounds.SoundSource.HOSTILE,
            1.0f,
            1.0f / (level.random.nextFloat() * 0.4f + 0.8f)
        )
    }
    
    /**
     * Handle Enderman teleport ability (60 blocks in facing direction)
     */
    private fun handleEndermanTeleport(player: ServerPlayer, enderman: EnderMan, yaw: Float, pitch: Float) {
        // Check cooldown (1000ms between teleports)
        val now = System.currentTimeMillis()
        val lastTeleport = attackCooldowns[player.uuid] ?: 0L
        if (now - lastTeleport < 1000) return
        attackCooldowns[player.uuid] = now
        
        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return
        
        // Calculate teleport direction
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        
        val lookX = -Math.sin(yawRad) * Math.cos(pitchRad)
        val lookY = -Math.sin(pitchRad)
        val lookZ = Math.cos(yawRad) * Math.cos(pitchRad)
        
        // Teleport 60 blocks in facing direction
        val teleportDistance = 60.0
        var targetX = enderman.x + lookX * teleportDistance
        var targetY = enderman.y + lookY * teleportDistance
        var targetZ = enderman.z + lookZ * teleportDistance
        
        // Clamp Y to valid range
        targetY = targetY.coerceIn(-64.0, 320.0)
        
        // Try to find a safe landing spot (check for solid ground)
        var foundSafe = false
        for (yOffset in 0..10) {
            val checkY = targetY - yOffset
            val blockPos = net.minecraft.core.BlockPos.containing(targetX, checkY, targetZ)
            val belowPos = blockPos.below()
            
            if (level.getBlockState(belowPos).isSolid &&
                !level.getBlockState(blockPos).isSolid &&
                !level.getBlockState(blockPos.above()).isSolid) {
                targetY = checkY
                foundSafe = true
                break
            }
        }
        
        // If no safe spot found, try above
        if (!foundSafe) {
            for (yOffset in 1..10) {
                val checkY = targetY + yOffset
                val blockPos = net.minecraft.core.BlockPos.containing(targetX, checkY, targetZ)
                val belowPos = blockPos.below()
                
                if (level.getBlockState(belowPos).isSolid &&
                    !level.getBlockState(blockPos).isSolid &&
                    !level.getBlockState(blockPos.above()).isSolid) {
                    targetY = checkY
                    foundSafe = true
                    break
                }
            }
        }
        
        // Teleport the enderman
        enderman.teleportTo(targetX, targetY, targetZ)
        
        // Play enderman teleport sounds at both locations
        level.playSound(
            null,
            enderman.xo, enderman.yo, enderman.zo,
            net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
            net.minecraft.sounds.SoundSource.HOSTILE,
            1.0f,
            1.0f
        )
        level.playSound(
            null,
            targetX, targetY, targetZ,
            net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
            net.minecraft.sounds.SoundSource.HOSTILE,
            1.0f,
            1.0f
        )
        
        // Spawn particles at destination
        level.sendParticles(
            net.minecraft.core.particles.ParticleTypes.PORTAL,
            targetX, targetY + 1.0, targetZ,
            50, 0.5, 1.0, 0.5, 0.1
        )
        
        // Notify player
        player.displayClientMessage(Component.literal("§5✦ Teleported!"), true)
    }
}
