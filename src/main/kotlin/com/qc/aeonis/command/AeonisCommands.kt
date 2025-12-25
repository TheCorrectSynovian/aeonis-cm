package com.qc.aeonis.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import com.qc.aeonis.config.AeonisFeatures
import com.qc.aeonis.network.AeonisNetworking
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.commands.arguments.ResourceArgument
import net.minecraft.core.registries.Registries
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LightningBolt
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.network.protocol.game.ClientboundGameEventPacket

object AeonisCommands {
    
    // Track original gamemode for untransform
    private val originalGameModes = mutableMapOf<java.util.UUID, GameType>()
    private val transformedEntities = mutableMapOf<java.util.UUID, Entity>()
    
    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, registryAccess, _ ->
            registerCommands(dispatcher)
            registerTransformCommand(dispatcher, registryAccess)
        }
    }
    
    private fun registerTransformCommand(
        dispatcher: CommandDispatcher<CommandSourceStack>,
        registryAccess: net.minecraft.commands.CommandBuildContext
    ) {
        // /transform <entity> - Spawn and spectate an entity
        dispatcher.register(
            Commands.literal("transform")
                .requires { it.hasPermission(2) }
                .then(Commands.argument("entity", ResourceArgument.resource(registryAccess, Registries.ENTITY_TYPE))
                    .executes { transformIntoEntity(it) })
        )
        
        // /untransform - Exit spectator and kill the entity
        dispatcher.register(
            Commands.literal("untransform")
                .requires { it.hasPermission(2) }
                .executes { untransform(it) }
        )
    }
    
    private fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("aeonis")
                .then(Commands.literal("help").executes { showHelp(it) })
                .then(Commands.literal("smite")
                    .then(Commands.argument("target", EntityArgument.player())
                        .requires { it.hasPermission(2) }
                        .executes { smitePlayer(it) }))
                .then(Commands.literal("yeet")
                    .then(Commands.argument("target", EntityArgument.player())
                        .requires { it.hasPermission(2) }
                        .executes { yeetPlayer(it) }))
                .then(Commands.literal("disco")
                    .then(Commands.argument("target", EntityArgument.player())
                        .requires { it.hasPermission(2) }
                        .executes { discoMode(it) }))
                .then(Commands.literal("supersize")
                    .then(Commands.argument("target", EntityArgument.player())
                        .requires { it.hasPermission(2) }
                        .executes { supersizePlayer(it) }))
                .then(Commands.literal("smol")
                    .then(Commands.argument("target", EntityArgument.player())
                        .requires { it.hasPermission(2) }
                        .executes { shrinkPlayer(it) }))
                .then(Commands.literal("chaos")
                    .then(Commands.argument("target", EntityArgument.player())
                        .requires { it.hasPermission(2) }
                        .executes { chaosMode(it) }))
                .then(Commands.literal("rocket")
                    .then(Commands.argument("target", EntityArgument.player())
                        .requires { it.hasPermission(2) }
                        .executes { rocketPlayer(it) }))
                .then(Commands.literal("spin")
                    .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("times", IntegerArgumentType.integer(1, 50))
                            .requires { it.hasPermission(2) }
                            .executes { spinPlayer(it) })))
                .then(Commands.literal("freeze")
                    .then(Commands.argument("target", EntityArgument.player())
                        .requires { it.hasPermission(2) }
                        .executes { freezePlayer(it) }))
                .then(Commands.literal("burn")
                    .then(Commands.argument("target", EntityArgument.player())
                        .requires { it.hasPermission(2) }
                        .executes { burnPlayer(it) }))
                .then(Commands.literal("do")
                    .then(Commands.literal("roar")
                        .requires { it.hasPermission(2) }
                        .executes { wardenRoar(it) })
                    .then(Commands.literal("darkness")
                        .requires { it.hasPermission(2) }
                        .executes { darknessPulse(it) }))
                .then(Commands.literal("features")
                    .then(Commands.literal("extra_mobs")
                        .requires { it.hasPermission(2) }
                        .executes { showExtraMobsStatus(it) }
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                            .executes { setExtraMobs(it) })))
        )
    }
    
    private fun showHelp(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        source.sendSuccess({ Component.literal("§6=== Aeonis Plus Commands ===") }, false)
        source.sendSuccess({ Component.literal("§e/aeonis smite <player>§7 - Strike with lightning") }, false)
        source.sendSuccess({ Component.literal("§e/aeonis yeet <player>§7 - Launch into the sky") }, false)
        source.sendSuccess({ Component.literal("§e/aeonis disco <player>§7 - Party time!") }, false)
        source.sendSuccess({ Component.literal("§e/aeonis supersize <player>§7 - Become HUGE") }, false)
        source.sendSuccess({ Component.literal("§e/aeonis smol <player>§7 - Become tiny") }, false)
        source.sendSuccess({ Component.literal("§e/aeonis chaos <player>§7 - Random effects!") }, false)
        source.sendSuccess({ Component.literal("§e/aeonis rocket <player>§7 - Rocket launch!") }, false)
        source.sendSuccess({ Component.literal("§e/aeonis spin <player> <times>§7 - Spin around") }, false)
        source.sendSuccess({ Component.literal("§e/aeonis freeze <player>§7 - Freeze in place") }, false)
        source.sendSuccess({ Component.literal("§e/aeonis burn <player>§7 - Set on fire") }, false)
        source.sendSuccess({ Component.literal("§5=== Abilities ===") }, false)
        source.sendSuccess({ Component.literal("§d/aeonis do roar§7 - Warden roar! Knockback nearby players") }, false)
        source.sendSuccess({ Component.literal("§d/aeonis do darkness§7 - Darkness pulse to all nearby") }, false)
        source.sendSuccess({ Component.literal("§a=== Entity Transform ===") }, false)
        source.sendSuccess({ Component.literal("§b/transform <entity>§7 - Become any mob! (spectate mode)") }, false)
        source.sendSuccess({ Component.literal("§b/untransform§7 - Return to normal") }, false)
        source.sendSuccess({ Component.literal("§2=== Features ===") }, false)
        source.sendSuccess({ Component.literal("§a/aeonis features extra_mobs <true/false>§7 - Toggle Aeonis mobs") }, false)
        return 1
    }
    
    // ========== FEATURES ==========
    
    private fun showExtraMobsStatus(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val server = source.server
        val enabled = AeonisFeatures.isExtraMobsEnabled(server)
        val status = if (enabled) "§aENABLED" else "§cDISABLED"
        source.sendSuccess({ Component.literal("§6Aeonis Extra Mobs: $status") }, false)
        return 1
    }
    
    private fun setExtraMobs(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val server = source.server
        val enabled = BoolArgumentType.getBool(ctx, "enabled")
        
        AeonisFeatures.setExtraMobsEnabled(server, enabled)
        
        if (enabled) {
            source.sendSuccess({ Component.literal("§a§l✓ Aeonis Extra Mobs ENABLED!") }, true)
            source.sendSuccess({ Component.literal("§7Stalkers will now spawn at night in the Overworld.") }, false)
        } else {
            source.sendSuccess({ Component.literal("§c§l✗ Aeonis Extra Mobs DISABLED!") }, true)
            source.sendSuccess({ Component.literal("§7Stalkers will no longer spawn naturally.") }, false)
        }
        
        return 1
    }
    
    // ========== ENTITY TRANSFORM ==========
    
    @Suppress("UNCHECKED_CAST")
    private fun transformIntoEntity(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val player = source.player as? ServerPlayer ?: run {
            source.sendFailure(Component.literal("§cOnly players can transform!"))
            return 0
        }
        val level = player.level() as net.minecraft.server.level.ServerLevel
        
        // Get entity type from argument
        val entityTypeRef = ResourceArgument.getEntityType(ctx, "entity")
        val entityType = entityTypeRef.value() as? EntityType<Entity> ?: run {
            source.sendFailure(Component.literal("§cInvalid entity type!"))
            return 0
        }
        
        // Check if already transformed
        if (transformedEntities.containsKey(player.uuid)) {
            source.sendFailure(Component.literal("§cYou're already transformed! Use /untransform first."))
            return 0
        }
        
        // Spawn the entity at player's location
        val entity = entityType.spawn(
            level,
            player.blockPosition(),
            EntitySpawnReason.COMMAND
        )
        
        if (entity == null) {
            source.sendFailure(Component.literal("§cFailed to spawn entity!"))
            return 0
        }
        
        // Store original gamemode and entity reference
        originalGameModes[player.uuid] = player.gameMode.gameModeForPlayer
        transformedEntities[player.uuid] = entity
        
        // Disable the mob's AI so it doesn't interfere with player control
        if (entity is Mob) {
            entity.setNoAi(true)
        }
        
        // Register entity for control
        AeonisNetworking.setControlledEntity(player, entity.id)
        
        // Set player to spectator mode
        player.setGameMode(GameType.SPECTATOR)
        
        // Make player spectate the entity
        player.setCamera(entity)
        
        val entityName = entityType.description.string
        source.sendSuccess({ 
            Component.literal("§a✨ You transformed into a §b$entityName§a! Use WASD to move, SPACE to jump. §e/untransform§a to return.") 
        }, true)
        return 1
    }
    
    private fun untransform(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val player = source.player as? ServerPlayer ?: run {
            source.sendFailure(Component.literal("§cOnly players can untransform!"))
            return 0
        }
        
        // Check if transformed
        val entity = transformedEntities.remove(player.uuid)
        if (entity == null) {
            source.sendFailure(Component.literal("§cYou're not transformed!"))
            return 0
        }
        
        // Stop controlling
        AeonisNetworking.removeControlledEntity(player)
        
        // Stop spectating
        player.setCamera(player)
        
        // Restore original gamemode
        val originalMode = originalGameModes.remove(player.uuid) ?: GameType.SURVIVAL
        player.setGameMode(originalMode)
        
        // Teleport player to entity's last position before killing it
        player.teleportTo(entity.x, entity.y, entity.z)
        
        // Kill the spawned entity
        entity.discard()
        
        source.sendSuccess({ 
            Component.literal("§a✨ You returned to your normal form!") 
        }, true)
        return 1
    }
    
    private fun smitePlayer(ctx: CommandContext<CommandSourceStack>): Int {
        val target = EntityArgument.getPlayer(ctx, "target")
        val level = target.level() as? net.minecraft.server.level.ServerLevel ?: return 0
        
        val lightning = LightningBolt(EntityType.LIGHTNING_BOLT, level)
        lightning.setPos(target.x, target.y, target.z)
        lightning.setVisualOnly(false)
        level.addFreshEntity(lightning)
        
        ctx.source.sendSuccess({ Component.literal("§c⚡ ${target.name.string} has been smitten!") }, true)
        return 1
    }
    
    private fun yeetPlayer(ctx: CommandContext<CommandSourceStack>): Int {
        val target = EntityArgument.getPlayer(ctx, "target")
        target.setDeltaMovement(target.deltaMovement.add(0.0, 3.0, 0.0))
        target.hurtMarked = true
        
        val level = target.level() as? net.minecraft.server.level.ServerLevel ?: return 0
        level.playSound(null, target.x, target.y, target.z, 
            SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0f, 1.0f)
        
        ctx.source.sendSuccess({ Component.literal("§b🚀 ${target.name.string} has been YEETED!") }, true)
        return 1
    }
    
    private fun discoMode(ctx: CommandContext<CommandSourceStack>): Int {
        val target = EntityArgument.getPlayer(ctx, "target")
        val level = target.level() as? net.minecraft.server.level.ServerLevel ?: return 0
        
        // Give glowing, speed, and jump boost
        target.addEffect(MobEffectInstance(MobEffects.GLOWING, 200, 0))
        target.addEffect(MobEffectInstance(MobEffects.SPEED, 200, 2))
        target.addEffect(MobEffectInstance(MobEffects.JUMP_BOOST, 200, 1))
        
        // Spawn colorful particles
        repeat(50) {
            level.sendParticles(ParticleTypes.NOTE,
                target.x + (Math.random() - 0.5) * 2,
                target.y + Math.random() * 2,
                target.z + (Math.random() - 0.5) * 2,
                1, 0.0, 0.0, 0.0, 0.0)
        }
        
        level.playSound(null, target.x, target.y, target.z,
            SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0f, 1.0f)
        
        ctx.source.sendSuccess({ Component.literal("§d🎵 ${target.name.string} is now in DISCO MODE!") }, true)
        return 1
    }
    
    private fun supersizePlayer(ctx: CommandContext<CommandSourceStack>): Int {
        val target = EntityArgument.getPlayer(ctx, "target")
        
        // Slowness + resistance + strength to simulate being big
        target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, 400, 1))
        target.addEffect(MobEffectInstance(MobEffects.RESISTANCE, 400, 1))
        target.addEffect(MobEffectInstance(MobEffects.STRENGTH, 400, 2))
        target.addEffect(MobEffectInstance(MobEffects.HEALTH_BOOST, 400, 4))
        
        ctx.source.sendSuccess({ Component.literal("§a🦖 ${target.name.string} is now SUPERSIZED!") }, true)
        return 1
    }
    
    private fun shrinkPlayer(ctx: CommandContext<CommandSourceStack>): Int {
        val target = EntityArgument.getPlayer(ctx, "target")
        
        // Speed + weakness + invisibility to simulate being small
        target.addEffect(MobEffectInstance(MobEffects.SPEED, 400, 2))
        target.addEffect(MobEffectInstance(MobEffects.WEAKNESS, 400, 1))
        target.addEffect(MobEffectInstance(MobEffects.INVISIBILITY, 400, 0))
        target.addEffect(MobEffectInstance(MobEffects.JUMP_BOOST, 400, 2))
        
        ctx.source.sendSuccess({ Component.literal("§7🐜 ${target.name.string} is now smol!") }, true)
        return 1
    }
    
    private fun chaosMode(ctx: CommandContext<CommandSourceStack>): Int {
        val target = EntityArgument.getPlayer(ctx, "target")
        val random = target.random
        
        val effects = listOf(
            MobEffects.LEVITATION,
            MobEffects.BLINDNESS,
            MobEffects.NAUSEA,
            MobEffects.SPEED,
            MobEffects.SLOWNESS,
            MobEffects.JUMP_BOOST,
            MobEffects.GLOWING,
            MobEffects.NIGHT_VISION,
            MobEffects.POISON,
            MobEffects.REGENERATION
        )
        
        repeat(3) {
            val effect = effects[random.nextInt(effects.size)]
            val amplifier = random.nextInt(3)
            target.addEffect(MobEffectInstance(effect, 200, amplifier))
        }
        
        ctx.source.sendSuccess({ Component.literal("§c🎲 ${target.name.string} is experiencing CHAOS!") }, true)
        return 1
    }
    
    private fun rocketPlayer(ctx: CommandContext<CommandSourceStack>): Int {
        val target = EntityArgument.getPlayer(ctx, "target")
        val level = target.level() as? net.minecraft.server.level.ServerLevel ?: return 0
        
        // Launch high with slow falling
        target.setDeltaMovement(Vec3(0.0, 5.0, 0.0))
        target.hurtMarked = true
        target.addEffect(MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0))
        
        // Particles and sound
        level.sendParticles(ParticleTypes.FLAME, target.x, target.y, target.z, 30, 0.3, 0.1, 0.3, 0.1)
        level.playSound(null, target.x, target.y, target.z,
            SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0f, 0.5f)
        
        ctx.source.sendSuccess({ Component.literal("§6🚀 ${target.name.string} has been LAUNCHED!") }, true)
        return 1
    }
    
    private fun spinPlayer(ctx: CommandContext<CommandSourceStack>): Int {
        val target = EntityArgument.getPlayer(ctx, "target")
        val times = IntegerArgumentType.getInteger(ctx, "times")
        
        // Apply nausea for spinning effect
        target.addEffect(MobEffectInstance(MobEffects.NAUSEA, times * 20, 0))
        
        // Rotate player rapidly
        val newYaw = target.yRot + (360f * times)
        target.setYRot(newYaw)
        
        ctx.source.sendSuccess({ Component.literal("§e🌀 ${target.name.string} is spinning ${times} times!") }, true)
        return 1
    }
    
    private fun freezePlayer(ctx: CommandContext<CommandSourceStack>): Int {
        val target = EntityArgument.getPlayer(ctx, "target")
        val level = target.level() as? net.minecraft.server.level.ServerLevel ?: return 0
        
        target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, 100, 255))
        target.addEffect(MobEffectInstance(MobEffects.JUMP_BOOST, 100, 128))
        target.ticksFrozen = 300
        
        level.sendParticles(ParticleTypes.SNOWFLAKE, target.x, target.y + 1, target.z, 20, 0.5, 0.5, 0.5, 0.0)
        level.playSound(null, target.x, target.y, target.z,
            SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 1.5f)
        
        ctx.source.sendSuccess({ Component.literal("§b❄ ${target.name.string} has been FROZEN!") }, true)
        return 1
    }
    
    private fun burnPlayer(ctx: CommandContext<CommandSourceStack>): Int {
        val target = EntityArgument.getPlayer(ctx, "target")
        val level = target.level() as? net.minecraft.server.level.ServerLevel ?: return 0
        
        target.remainingFireTicks = 100
        level.sendParticles(ParticleTypes.FLAME, target.x, target.y + 1, target.z, 30, 0.5, 0.5, 0.5, 0.05)
        
        ctx.source.sendSuccess({ Component.literal("§c🔥 ${target.name.string} is on FIRE!") }, true)
        return 1
    }
    
    // ========== TRANSFORM ABILITIES ==========
    
    private fun wardenRoar(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val player = source.player ?: run {
            source.sendFailure(Component.literal("§cOnly players can use this!"))
            return 0
        }
        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return 0
        
        // Play warden roar sound
        level.playSound(
            null, player.x, player.y, player.z,
            SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS,
            3.0f, 0.8f
        )
        
        // Sonic boom particles in a ring
        val radius = 8.0
        for (i in 0 until 36) {
            val angle = Math.toRadians(i * 10.0)
            val px = player.x + Math.cos(angle) * radius
            val pz = player.z + Math.sin(angle) * radius
            level.sendParticles(
                ParticleTypes.SONIC_BOOM,
                px, player.y + 1.0, pz,
                1, 0.0, 0.0, 0.0, 0.0
            )
        }
        
        // Knockback all nearby players (within 10 blocks)
        val nearbyPlayers = level.getEntitiesOfClass(
            Player::class.java,
            AABB.ofSize(player.position(), 20.0, 10.0, 20.0)
        ) { it != player }
        
        for (target in nearbyPlayers) {
            val direction = target.position().subtract(player.position()).normalize()
            val knockback = direction.multiply(2.5, 1.0, 2.5).add(0.0, 0.8, 0.0)
            target.setDeltaMovement(knockback)
            target.hurtMarked = true
            
            // Apply brief weakness
            if (target is ServerPlayer) {
                target.addEffect(MobEffectInstance(MobEffects.WEAKNESS, 60, 1))
            }
        }
        
        val count = nearbyPlayers.size
        source.sendSuccess({ 
            Component.literal("§5🔊 You unleash a WARDEN ROAR! $count player(s) affected!") 
        }, true)
        return 1
    }
    
    private fun darknessPulse(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val player = source.player ?: run {
            source.sendFailure(Component.literal("§cOnly players can use this!"))
            return 0
        }
        val level = player.level() as? net.minecraft.server.level.ServerLevel ?: return 0
        
        // Play warden heartbeat/darkness sounds
        level.playSound(
            null, player.x, player.y, player.z,
            SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS,
            2.0f, 0.5f
        )
        level.playSound(
            null, player.x, player.y, player.z,
            SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS,
            1.5f, 0.7f
        )
        
        // Dark particles emanating outward
        for (i in 0 until 50) {
            val angle = Math.random() * Math.PI * 2
            val dist = Math.random() * 8.0
            val px = player.x + Math.cos(angle) * dist
            val pz = player.z + Math.sin(angle) * dist
            level.sendParticles(
                ParticleTypes.SCULK_SOUL,
                px, player.y + Math.random() * 2.0, pz,
                1, 0.2, 0.2, 0.2, 0.02
            )
        }
        
        // Apply darkness to all nearby players (within 15 blocks)
        val nearbyPlayers = level.getEntitiesOfClass(
            Player::class.java,
            AABB.ofSize(player.position(), 30.0, 15.0, 30.0)
        ) { it != player }
        
        for (target in nearbyPlayers) {
            if (target is ServerPlayer) {
                // Apply darkness effect (like warden)
                target.addEffect(MobEffectInstance(MobEffects.DARKNESS, 200, 0))
                // Also apply brief blindness for extra spook
                target.addEffect(MobEffectInstance(MobEffects.BLINDNESS, 40, 0))
            }
        }
        
        val count = nearbyPlayers.size
        source.sendSuccess({ 
            Component.literal("§8🌑 You release a DARKNESS PULSE! $count player(s) blinded!") 
        }, true)
        return 1
    }
}
