package com.qc.aeonis.minigame.manhunt

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.qc.aeonis.network.AeonisNetworking
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.permissions.Permissions
import org.slf4j.LoggerFactory

/**
 * ╔════════════════════════════════════════════════════════════════════════════╗
 * ║                      MANHUNT COMMANDS                                      ║
 * ║                                                                            ║
 * ║  Comprehensive command registration for the Manhunt minigame.              ║
 * ║                                                                            ║
 * ║  Command Structure:                                                        ║
 * ║  /manhunt                     - Opens setup GUI (if no args)               ║
 * ║    ├── create                 - Create a new game                          ║
 * ║    ├── join <gameId>          - Join an existing game                      ║
 * ║    ├── leave                  - Leave current game                         ║
 * ║    ├── start                  - Start the game (creator only)              ║
 * ║    ├── stop                   - Stop the game (admin)                      ║
 * ║    ├── list                   - List active games                          ║
 * ║    ├── settings               - Configure game settings                    ║
 * ║    │   ├── difficulty <value> - Set difficulty (0.5-2.0)                   ║
 * ║    │   ├── headstart <secs>   - Set head start time                        ║
 * ║    │   └── respawns <count>   - Set max respawns                           ║
 * ║    └── info                   - Show current game info                     ║
 * ║                                                                            ║
 * ║  /hunter                                                                   ║
 * ║    ├── deactivate             - Freeze the hunter                          ║
 * ║    ├── activate               - Unfreeze the hunter                        ║
 * ║    └── teleport               - TP hunter to nearest player                ║
 * ╚════════════════════════════════════════════════════════════════════════════╝
 */
object ManhuntCommands {
    private val logger = LoggerFactory.getLogger("aeonis-manhunt-commands")
    
    // Temporary settings storage for game creation
    private val pendingSettings = mutableMapOf<java.util.UUID, ManhuntSettings>()
    
    /**
     * Registers all Manhunt commands and event handlers.
     */
    fun register() {
        // Register commands
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            registerCommands(dispatcher)
        }
        
        // Register death event handler
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, _ ->
            if (entity is ServerPlayer) {
                ManhuntManager.onPlayerDeath(entity)
            }
        }
        
        // Handle player disconnect
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            val player = handler.player
            val game = ManhuntManager.getPlayerGame(player.uuid)
            if (game != null) {
                ManhuntManager.leaveGame(player)
            }
        }
        
        logger.info("§a[Manhunt] §7Commands registered!")
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // COMMAND REGISTRATION
    // ════════════════════════════════════════════════════════════════════════
    
    private fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        // /manhunt - Main command
        dispatcher.register(
            Commands.literal("manhunt")
                // No args - open GUI (sends packet to client)
                .executes { openSetupGui(it) }
                
                // Help
                .then(Commands.literal("help")
                    .executes { showHelp(it) })
                
                // Create game
                .then(Commands.literal("create")
                    .executes { createGame(it) }
                    .then(Commands.argument("difficulty", StringArgumentType.word())
                        .suggests { _, builder ->
                            builder.suggest("easy")
                            builder.suggest("normal")
                            builder.suggest("hard")
                            builder.suggest("nightmare")
                            builder.buildFuture()
                        }
                        .executes { createGameWithDifficulty(it) }))
                
                // Join game
                .then(Commands.literal("join")
                    .then(Commands.argument("gameId", StringArgumentType.word())
                        .suggests { _, builder ->
                            ManhuntManager.getActiveGameIds().forEach { builder.suggest(it) }
                            builder.buildFuture()
                        }
                        .executes { joinGame(it) }))
                
                // Leave game
                .then(Commands.literal("leave")
                    .executes { leaveGame(it) })
                
                // Start game
                .then(Commands.literal("start")
                    .executes { startGame(it) })
                
                // Stop game
                .then(Commands.literal("stop")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .executes { stopGame(it) }
                    .then(Commands.argument("gameId", StringArgumentType.word())
                        .executes { stopSpecificGame(it) }))
                
                // List games
                .then(Commands.literal("list")
                    .executes { listGames(it) })
                
                // Game info
                .then(Commands.literal("info")
                    .executes { showGameInfo(it) })
                
                // Settings subcommands
                .then(Commands.literal("settings")
                    .then(Commands.literal("difficulty")
                        .then(Commands.argument("value", FloatArgumentType.floatArg(0.5f, 2.0f))
                            .executes { setDifficulty(it) }))
                    .then(Commands.literal("headstart")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 120))
                            .executes { setHeadStart(it) }))
                    .then(Commands.literal("respawns")
                        .then(Commands.argument("count", IntegerArgumentType.integer(-1, 10))
                            .executes { setRespawns(it) }))
                    .then(Commands.literal("blocks")
                        .then(Commands.argument("count", IntegerArgumentType.integer(0, 256))
                            .executes { setHunterBlocks(it) }))
                    .then(Commands.literal("show")
                        .executes { showSettings(it) }))
                
                // Quick start with players
                .then(Commands.literal("quickstart")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .executes { quickStart(it) }
                    .then(Commands.argument("players", EntityArgument.players())
                        .executes { quickStartWithPlayers(it) }))
        )
        
        // /hunter - Hunter management commands
        dispatcher.register(
            Commands.literal("hunter")
                .then(Commands.literal("deactivate")
                    .executes { deactivateHunter(it) })
                .then(Commands.literal("activate")
                    .executes { activateHunter(it) })
                .then(Commands.literal("freeze")
                    .executes { deactivateHunter(it) })
                .then(Commands.literal("unfreeze")
                    .executes { activateHunter(it) })
                .then(Commands.literal("teleport")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .executes { teleportHunter(it) })
                .then(Commands.literal("giveblocks")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 256))
                        .executes { giveHunterBlocks(it) }))
                .then(Commands.literal("info")
                    .executes { showHunterInfo(it) })
        )
        
        // Short alias
        dispatcher.register(
            Commands.literal("mh")
                .redirect(dispatcher.getRoot().getChild("manhunt"))
        )
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // GUI COMMANDS
    // ════════════════════════════════════════════════════════════════════════
    
    private fun openSetupGui(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException
        
        // Send packet to client to open GUI
        AeonisNetworking.sendOpenManhuntGuiPacket(player as ServerPlayer)
        
        return 1
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // GAME MANAGEMENT COMMANDS
    // ════════════════════════════════════════════════════════════════════════
    
    private fun showHelp(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        source.sendSuccess({ Component.literal("""
            §6§l═══════ MANHUNT HELP ═══════
            §e/manhunt§7 - Open setup GUI
            §e/manhunt create [difficulty]§7 - Create a game
            §e/manhunt join <gameId>§7 - Join a game
            §e/manhunt leave§7 - Leave current game
            §e/manhunt start§7 - Start the game
            §e/manhunt stop§7 - Stop the game
            §e/manhunt list§7 - List active games
            §e/manhunt info§7 - Show current game info
            
            §b§lSettings:
            §e/manhunt settings difficulty <0.5-2.0>
            §e/manhunt settings headstart <seconds>
            §e/manhunt settings respawns <count>
            
            §c§lHunter Commands:
            §e/hunter deactivate§7 - Freeze hunter
            §e/hunter activate§7 - Unfreeze hunter
            
            §7§oDifficulties: easy (0.5), normal (1.0), hard (1.5), nightmare (2.0)
        """.trimIndent()) }, false)
        return 1
    }
    
    private fun createGame(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException as ServerPlayer
        val level = player.level() as ServerLevel
        
        // Get or create settings for this player
        val settings = pendingSettings.getOrPut(player.uuid) { ManhuntSettings() }
        
        val game = ManhuntManager.createGame(level, player, settings)
        if (game == null) {
            return 0
        }
        
        // Auto-join the creator
        ManhuntManager.joinGame(player, game.id)
        
        player.sendSystemMessage(Component.literal("§a[Manhunt] §7Game created! ID: §e${game.id}"))
        player.sendSystemMessage(Component.literal("§7Other players can join with: §e/manhunt join ${game.id}"))
        player.sendSystemMessage(Component.literal("§7Start the game with: §e/manhunt start"))
        
        // Clear pending settings
        pendingSettings.remove(player.uuid)
        
        return 1
    }
    
    private fun createGameWithDifficulty(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException as ServerPlayer
        val difficulty = StringArgumentType.getString(ctx, "difficulty")
        
        val settings = when (difficulty.lowercase()) {
            "easy" -> ManhuntSettings.easy()
            "normal" -> ManhuntSettings.normal()
            "hard" -> ManhuntSettings.hard()
            "nightmare" -> ManhuntSettings.nightmare()
            else -> ManhuntSettings.normal()
        }
        
        pendingSettings[player.uuid] = settings
        return createGame(ctx)
    }
    
    private fun joinGame(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException as ServerPlayer
        val gameId = StringArgumentType.getString(ctx, "gameId")
        
        return if (ManhuntManager.joinGame(player, gameId)) 1 else 0
    }
    
    private fun leaveGame(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException as ServerPlayer
        return if (ManhuntManager.leaveGame(player)) 1 else 0
    }
    
    private fun startGame(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException as ServerPlayer
        val game = ManhuntManager.getPlayerGame(player.uuid)
        
        if (game == null) {
            player.sendSystemMessage(Component.literal("§c[Manhunt] §7You're not in any game!"))
            return 0
        }
        
        // Only creator or admin can start
        if (game.creatorUUID != player.uuid && !ctx.source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            player.sendSystemMessage(Component.literal("§c[Manhunt] §7Only the game creator can start the game!"))
            return 0
        }
        
        if (game.players.size < 1) {
            player.sendSystemMessage(Component.literal("§c[Manhunt] §7Need at least 1 player to start!"))
            return 0
        }
        
        return if (ManhuntManager.startGame(game.id)) 1 else 0
    }
    
    private fun stopGame(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException as ServerPlayer
        val game = ManhuntManager.getPlayerGame(player.uuid)
        
        if (game == null) {
            player.sendSystemMessage(Component.literal("§c[Manhunt] §7You're not in any game!"))
            return 0
        }
        
        ManhuntManager.endGame(game.id, "Stopped by ${player.name.string}")
        return 1
    }
    
    private fun stopSpecificGame(ctx: CommandContext<CommandSourceStack>): Int {
        val gameId = StringArgumentType.getString(ctx, "gameId")
        val source = ctx.source
        
        val game = ManhuntManager.getGame(gameId)
        if (game == null) {
            source.sendFailure(Component.literal("§c[Manhunt] §7No game found with ID: $gameId"))
            return 0
        }
        
        ManhuntManager.endGame(gameId, "Stopped by admin")
        source.sendSuccess({ Component.literal("§a[Manhunt] §7Game stopped.") }, true)
        return 1
    }
    
    private fun listGames(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val gameIds = ManhuntManager.getActiveGameIds()
        
        if (gameIds.isEmpty()) {
            source.sendSuccess({ Component.literal("§e[Manhunt] §7No active games.") }, false)
            return 1
        }
        
        source.sendSuccess({ Component.literal("§6§l═══════ ACTIVE MANHUNT GAMES ═══════") }, false)
        
        for (gameId in gameIds) {
            val game = ManhuntManager.getGame(gameId) ?: continue
            val status = when (game.state) {
                ManhuntGameState.SETUP -> "§e[Setup]"
                ManhuntGameState.COUNTDOWN -> "§6[Starting]"
                ManhuntGameState.HEAD_START -> "§a[Head Start]"
                ManhuntGameState.ACTIVE -> "§c[Active]"
                ManhuntGameState.ENDED -> "§7[Ended]"
            }
            
            source.sendSuccess({ Component.literal(
                "$status §7$gameId - §a${game.players.size} players §7- ${game.getDifficultyName()}"
            ) }, false)
        }
        
        return 1
    }
    
    private fun showGameInfo(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException as ServerPlayer
        val game = ManhuntManager.getPlayerGame(player.uuid)
        
        if (game == null) {
            player.sendSystemMessage(Component.literal("§c[Manhunt] §7You're not in any game!"))
            return 0
        }
        
        val status = when (game.state) {
            ManhuntGameState.SETUP -> "§e[Setup]"
            ManhuntGameState.COUNTDOWN -> "§6[Starting]"
            ManhuntGameState.HEAD_START -> "§a[Head Start]"
            ManhuntGameState.ACTIVE -> "§c[Active]"
            ManhuntGameState.ENDED -> "§7[Ended]"
        }
        
        player.sendSystemMessage(Component.literal("§6§l═══════ GAME INFO ═══════"))
        player.sendSystemMessage(Component.literal("§7Game ID: §e${game.id}"))
        player.sendSystemMessage(Component.literal("§7Status: $status"))
        player.sendSystemMessage(Component.literal("§7Difficulty: ${game.getDifficultyName()}"))
        player.sendSystemMessage(Component.literal("§7Players: §a${game.players.size}"))
        player.sendSystemMessage(Component.literal("§7Alive: §a${game.getAliveSpeedrunners().size}"))
        player.sendSystemMessage(Component.literal("§7Time: §e${game.getElapsedTimeFormatted()}"))
        
        if (game.state == ManhuntGameState.HEAD_START) {
            val secondsLeft = game.headStartTicks / 20
            player.sendSystemMessage(Component.literal("§7Hunter releases in: §c${secondsLeft}s"))
        }
        
        return 1
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // SETTINGS COMMANDS
    // ════════════════════════════════════════════════════════════════════════
    
    private fun setDifficulty(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException as ServerPlayer
        val value = FloatArgumentType.getFloat(ctx, "value")
        
        val settings = pendingSettings.getOrPut(player.uuid) { ManhuntSettings() }
        settings.difficulty = value
        
        val diffName = when {
            value <= 0.5f -> "§aEasy"
            value <= 1.0f -> "§eNormal"
            value <= 1.5f -> "§cHard"
            else -> "§4Nightmare"
        }
        
        player.sendSystemMessage(Component.literal("§a[Manhunt] §7Difficulty set to: $diffName §7($value)"))
        return 1
    }
    
    private fun setHeadStart(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException as ServerPlayer
        val seconds = IntegerArgumentType.getInteger(ctx, "seconds")
        
        val settings = pendingSettings.getOrPut(player.uuid) { ManhuntSettings() }
        settings.headStartSeconds = seconds
        
        player.sendSystemMessage(Component.literal("§a[Manhunt] §7Head start set to: §e${seconds}s"))
        return 1
    }
    
    private fun setRespawns(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException as ServerPlayer
        val count = IntegerArgumentType.getInteger(ctx, "count")
        
        val settings = pendingSettings.getOrPut(player.uuid) { ManhuntSettings() }
        settings.maxRespawns = count
        settings.respawnEnabled = count != 0
        
        val respawnText = if (count < 0) "unlimited" else count.toString()
        player.sendSystemMessage(Component.literal("§a[Manhunt] §7Max respawns set to: §e$respawnText"))
        return 1
    }
    
    private fun setHunterBlocks(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException as ServerPlayer
        val count = IntegerArgumentType.getInteger(ctx, "count")
        
        val settings = pendingSettings.getOrPut(player.uuid) { ManhuntSettings() }
        settings.hunterStartBlocks = count
        
        player.sendSystemMessage(Component.literal("§a[Manhunt] §7Hunter starting blocks set to: §e$count"))
        return 1
    }
    
    private fun showSettings(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException as ServerPlayer
        val settings = pendingSettings.getOrPut(player.uuid) { ManhuntSettings() }
        
        val diffName = when {
            settings.difficulty <= 0.5f -> "§aEasy"
            settings.difficulty <= 1.0f -> "§eNormal"
            settings.difficulty <= 1.5f -> "§cHard"
            else -> "§4Nightmare"
        }
        
        player.sendSystemMessage(Component.literal("§6§l═══════ MANHUNT SETTINGS ═══════"))
        player.sendSystemMessage(Component.literal("§7Difficulty: $diffName §7(${settings.difficulty})"))
        player.sendSystemMessage(Component.literal("§7Head Start: §e${settings.headStartSeconds}s"))
        player.sendSystemMessage(Component.literal("§7Hunter Blocks: §e${settings.hunterStartBlocks}"))
        player.sendSystemMessage(Component.literal("§7Respawns: §e${if (settings.maxRespawns < 0) "unlimited" else settings.maxRespawns}"))
        player.sendSystemMessage(Component.literal("§7Win Condition: §e${settings.winCondition.displayName}"))
        
        return 1
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // QUICK START
    // ════════════════════════════════════════════════════════════════════════
    
    private fun quickStart(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException as ServerPlayer
        
        // Create and immediately start with just this player
        val settings = ManhuntSettings.normal()
        val level = player.level() as ServerLevel
        
        val game = ManhuntManager.createGame(level, player, settings)
        if (game == null) return 0
        
        ManhuntManager.joinGame(player, game.id)
        ManhuntManager.startGame(game.id)
        
        return 1
    }
    
    private fun quickStartWithPlayers(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val players = EntityArgument.getPlayers(ctx, "players")
        val creator = source.playerOrException as ServerPlayer
        
        val settings = ManhuntSettings.normal()
        val level = creator.level() as ServerLevel
        
        val game = ManhuntManager.createGame(level, creator, settings)
        if (game == null) return 0
        
        // Add all specified players
        for (player in players) {
            ManhuntManager.joinGame(player, game.id)
        }
        
        // Start the game
        ManhuntManager.startGame(game.id)
        
        source.sendSuccess({ Component.literal("§a[Manhunt] §7Quick started with ${players.size} players!") }, true)
        return 1
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // HUNTER COMMANDS
    // ════════════════════════════════════════════════════════════════════════
    
    private fun deactivateHunter(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException as ServerPlayer
        val game = ManhuntManager.getPlayerGame(player.uuid)
        
        if (game == null) {
            player.sendSystemMessage(Component.literal("§c[Manhunt] §7You're not in any game!"))
            return 0
        }
        
        // Check permission - creator or admin
        if (game.creatorUUID != player.uuid && !ctx.source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            player.sendSystemMessage(Component.literal("§c[Manhunt] §7Only the game creator or admin can control the hunter!"))
            return 0
        }
        
        return if (ManhuntManager.freezeHunter(game.id)) {
            player.sendSystemMessage(Component.literal("§a[Manhunt] §7Hunter deactivated."))
            1
        } else {
            player.sendSystemMessage(Component.literal("§c[Manhunt] §7Failed to deactivate hunter."))
            0
        }
    }
    
    private fun activateHunter(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException as ServerPlayer
        val game = ManhuntManager.getPlayerGame(player.uuid)
        
        if (game == null) {
            player.sendSystemMessage(Component.literal("§c[Manhunt] §7You're not in any game!"))
            return 0
        }
        
        // Check permission
        if (game.creatorUUID != player.uuid && !ctx.source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            player.sendSystemMessage(Component.literal("§c[Manhunt] §7Only the game creator or admin can control the hunter!"))
            return 0
        }
        
        return if (ManhuntManager.unfreezeHunter(game.id)) {
            player.sendSystemMessage(Component.literal("§a[Manhunt] §7Hunter activated."))
            1
        } else {
            player.sendSystemMessage(Component.literal("§c[Manhunt] §7Failed to activate hunter."))
            0
        }
    }
    
    private fun teleportHunter(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException as ServerPlayer
        val game = ManhuntManager.getPlayerGame(player.uuid)
        
        if (game == null) {
            player.sendSystemMessage(Component.literal("§c[Manhunt] §7You're not in any game!"))
            return 0
        }
        
        val hunterId = game.hunterEntityId
        if (hunterId == null) {
            player.sendSystemMessage(Component.literal("§c[Manhunt] §7No hunter spawned yet!"))
            return 0
        }
        
        val hunter = game.level.getEntity(hunterId) as? com.qc.aeonis.entity.HunterEntity
        if (hunter == null) {
            player.sendSystemMessage(Component.literal("§c[Manhunt] §7Hunter not found!"))
            return 0
        }
        
        // Find nearest alive speedrunner
        val nearestPlayer = game.getAliveSpeedrunners()
            .mapNotNull { game.level.server?.playerList?.getPlayer(it) }
            .minByOrNull { it.distanceToSqr(hunter) }
        
        if (nearestPlayer == null) {
            player.sendSystemMessage(Component.literal("§c[Manhunt] §7No alive speedrunners to teleport to!"))
            return 0
        }
        
        // Teleport hunter near the player
        val pos = nearestPlayer.blockPosition()
        hunter.teleportTo(pos.x + 10.0, pos.y.toDouble(), pos.z + 10.0)
        
        game.broadcast("§c⚠ §7Hunter has been teleported by admin!")
        return 1
    }
    
    private fun giveHunterBlocks(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException as ServerPlayer
        val count = IntegerArgumentType.getInteger(ctx, "count")
        val game = ManhuntManager.getPlayerGame(player.uuid)
        
        if (game == null) {
            player.sendSystemMessage(Component.literal("§c[Manhunt] §7You're not in any game!"))
            return 0
        }
        
        val hunterId = game.hunterEntityId
        if (hunterId == null) {
            player.sendSystemMessage(Component.literal("§c[Manhunt] §7No hunter spawned yet!"))
            return 0
        }
        
        val hunter = game.level.getEntity(hunterId) as? com.qc.aeonis.entity.HunterEntity
        if (hunter == null) {
            player.sendSystemMessage(Component.literal("§c[Manhunt] §7Hunter not found!"))
            return 0
        }
        
        hunter.giveBlocks(count)
        player.sendSystemMessage(Component.literal("§a[Manhunt] §7Gave hunter $count blocks."))
        return 1
    }
    
    private fun showHunterInfo(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException as ServerPlayer
        val game = ManhuntManager.getPlayerGame(player.uuid)
        
        if (game == null) {
            player.sendSystemMessage(Component.literal("§c[Manhunt] §7You're not in any game!"))
            return 0
        }
        
        val hunterId = game.hunterEntityId
        if (hunterId == null) {
            player.sendSystemMessage(Component.literal("§e[Manhunt] §7Hunter not spawned yet."))
            return 1
        }
        
        val hunter = game.level.getEntity(hunterId) as? com.qc.aeonis.entity.HunterEntity
        if (hunter == null) {
            player.sendSystemMessage(Component.literal("§c[Manhunt] §7Hunter not found!"))
            return 0
        }
        
        val state = hunter.currentState.name
        val health = "%.1f".format(hunter.health)
        val maxHealth = "%.1f".format(hunter.maxHealth)
        val pos = hunter.blockPosition()
        
        player.sendSystemMessage(Component.literal("§6§l═══════ HUNTER INFO ═══════"))
        player.sendSystemMessage(Component.literal("§7State: §e$state"))
        player.sendSystemMessage(Component.literal("§7Health: §c$health§7/§c$maxHealth"))
        player.sendSystemMessage(Component.literal("§7Position: §e${pos.x}, ${pos.y}, ${pos.z}"))
        player.sendSystemMessage(Component.literal("§7Difficulty: §e${hunter.difficultyMultiplier}x"))
        
        return 1
    }
}
