package com.qc.aeonis.minigame.prophunt

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.coordinates.BlockPosArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Items
import org.slf4j.LoggerFactory

/**
 * ╔════════════════════════════════════════════════════════════════════════════╗
 * ║                      PROP HUNT COMMANDS                                    ║
 * ║                                                                            ║
 * ║  Comprehensive command registration for the Prop Hunt minigame.            ║
 * ║  Includes player commands, admin commands, and arena management.           ║
 * ║                                                                            ║
 * ║  Command Structure:                                                        ║
 * ║  /prophunt                                                                 ║
 * ║    ├── create <arena>         - Create a new game                          ║
 * ║    ├── join <arena>           - Join an existing game                      ║
 * ║    ├── leave                  - Leave current game                         ║
 * ║    ├── list                   - List active games                          ║
 * ║    ├── start                  - Force start (admin)                        ║
 * ║    ├── stop [arena]           - Stop a game (admin)                        ║
 * ║    ├── arena                  - Arena management subcommands               ║
 * ║    │   ├── create <id> <preset>                                            ║
 * ║    │   ├── set <id> <pos1> <pos2> <lobby>                                  ║
 * ║    │   ├── remove <id>                                                     ║
 * ║    │   └── list                                                            ║
 * ║    ├── settings               - Game settings (admin)                      ║
 * ║    └── stats [player]         - View statistics                            ║
 * ╚════════════════════════════════════════════════════════════════════════════╝
 */
object PropHuntCommands {
    private val logger = LoggerFactory.getLogger("aeonis-prophunt-commands")
    
    /**
     * Registers all Prop Hunt commands and event handlers.
     * Call this during mod initialization.
     */
    fun register() {
        // Register commands
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            registerCommands(dispatcher)
        }
        
        // Register event handlers
        registerEventHandlers()
        
        logger.info("§a[PropHunt] §7Commands and event handlers registered!")
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // COMMAND REGISTRATION
    // ════════════════════════════════════════════════════════════════════════
    
    private fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("prophunt")
                // === PLAYER COMMANDS ===
                .then(Commands.literal("help")
                    .executes { showHelp(it) })
                
                .then(Commands.literal("create")
                    .requires { it.hasPermission(2) }
                    .then(Commands.argument("arena", StringArgumentType.word())
                        .executes { createGame(it) }))
                
                .then(Commands.literal("join")
                    .then(Commands.argument("arena", StringArgumentType.word())
                        .executes { joinGame(it) }))
                
                .then(Commands.literal("leave")
                    .executes { leaveGame(it) })
                
                .then(Commands.literal("list")
                    .executes { listGames(it) })
                
                // === PROP COMMANDS (in-game) ===
                .then(Commands.literal("prop")
                    .then(Commands.literal("lock")
                        .executes { toggleLock(it) })
                    .then(Commands.literal("freeze")
                        .executes { toggleFreeze(it) })
                    .then(Commands.literal("taunt")
                        .executes { performTaunt(it) })
                    .then(Commands.literal("disguise")
                        .then(Commands.argument("type", StringArgumentType.word())
                            .executes { changeDisguise(it) })))
                
                // === HUNTER COMMANDS (in-game) ===
                .then(Commands.literal("hunter")
                    .then(Commands.literal("scan")
                        .executes { useScan(it) })
                    .then(Commands.literal("track")
                        .executes { useTracker(it) }))
                
                // === ADMIN COMMANDS ===
                .then(Commands.literal("start")
                    .requires { it.hasPermission(2) }
                    .executes { forceStart(it) })
                
                .then(Commands.literal("stop")
                    .requires { it.hasPermission(2) }
                    .executes { stopCurrentGame(it) }
                    .then(Commands.argument("arena", StringArgumentType.word())
                        .executes { stopGame(it) }))
                
                .then(Commands.literal("skip")
                    .requires { it.hasPermission(2) }
                    .executes { skipPhase(it) })
                
                // === ARENA MANAGEMENT ===
                .then(Commands.literal("arena")
                    .requires { it.hasPermission(2) }
                    .then(Commands.literal("create")
                        .then(Commands.argument("id", StringArgumentType.word())
                            .executes { createArenaHere(it) }
                            .then(Commands.argument("preset", StringArgumentType.word())
                                .executes { createArenaWithPreset(it) })))
                    .then(Commands.literal("set")
                        .then(Commands.argument("id", StringArgumentType.word())
                            .then(Commands.argument("pos1", BlockPosArgument.blockPos())
                                .then(Commands.argument("pos2", BlockPosArgument.blockPos())
                                    .then(Commands.argument("lobby", BlockPosArgument.blockPos())
                                        .executes { setCustomArena(it) })))))
                    .then(Commands.literal("remove")
                        .then(Commands.argument("id", StringArgumentType.word())
                            .executes { removeArena(it) }))
                    .then(Commands.literal("list")
                        .executes { listArenas(it) })
                    .then(Commands.literal("tp")
                        .then(Commands.argument("id", StringArgumentType.word())
                            .executes { teleportToArena(it) })))
                
                // === SETTINGS ===
                .then(Commands.literal("settings")
                    .requires { it.hasPermission(2) }
                    .then(Commands.literal("rounds")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 20))
                            .executes { setRounds(it) }))
                    .then(Commands.literal("hidetime")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(10, 120))
                            .executes { setHideTime(it) }))
                    .then(Commands.literal("roundtime")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(60, 600))
                            .executes { setRoundTime(it) }))
                    .then(Commands.literal("hunters")
                        .then(Commands.argument("min", IntegerArgumentType.integer(1, 10))
                            .then(Commands.argument("max", IntegerArgumentType.integer(1, 10))
                                .executes { setHunterCount(it) })))
                    .then(Commands.literal("hints")
                        .then(Commands.literal("on").executes { setHints(it, true) })
                        .then(Commands.literal("off").executes { setHints(it, false) }))
                    .then(Commands.literal("show")
                        .executes { showSettings(it) }))
                
                // === STATISTICS ===
                .then(Commands.literal("stats")
                    .executes { showStats(it) }
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes { showPlayerStats(it) }))
        )
        
        // Short aliases
        dispatcher.register(
            Commands.literal("ph")
                .redirect(dispatcher.getRoot().getChild("prophunt"))
        )
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // PLAYER COMMANDS
    // ════════════════════════════════════════════════════════════════════════
    
    private fun showHelp(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        source.sendSuccess({ Component.literal("""
            §6§l═══════ PROP HUNT HELP ═══════
            §e/prophunt create <arena>§7 - Create a new game
            §e/prophunt join <arena>§7 - Join a game
            §e/prophunt leave§7 - Leave your current game
            §e/prophunt list§7 - List active games
            §e/prophunt stats§7 - View your statistics
            
            §b§lProp Commands (in-game):
            §e/prophunt prop lock§7 - Lock rotation
            §e/prophunt prop freeze§7 - Freeze movement
            §e/prophunt prop taunt§7 - Perform a taunt
            
            §c§lHunter Commands (in-game):
            §e/prophunt hunter scan§7 - Use scanner
            §e/prophunt hunter track§7 - Use tracker dart
            §6§l════════════════════════════════
        """.trimIndent()) }, false)
        return 1
    }
    
    private fun createGame(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException
        val arenaId = StringArgumentType.getString(ctx, "arena")
        val level = ctx.source.level
        
        // Show experimental warning before creating game
        player.sendSystemMessage(Component.literal(""))
        player.sendSystemMessage(Component.literal("§6§l⚠ §c§lWARNING §6§l⚠"))
        player.sendSystemMessage(Component.literal("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━"))
        player.sendSystemMessage(Component.literal("§eProp Hunt is §c§lEXPERIMENTAL §eand §c§lWORK IN PROGRESS§e!"))
        player.sendSystemMessage(Component.literal("§7You may encounter bugs or incomplete features."))
        player.sendSystemMessage(Component.literal("§7Proceed at your own risk!"))
        player.sendSystemMessage(Component.literal("§7━━━━━━━━━━━━━━━━━━━━━━━━━━━━"))
        player.sendSystemMessage(Component.literal(""))
        
        val game = PropHuntManager.createGame(arenaId, level, player)
        return if (game != null) {
            ctx.source.sendSuccess({ Component.literal("§a[PropHunt] §7Game created! Use §e/prophunt join $arenaId§7 to join.") }, true)
            1
        } else {
            0
        }
    }
    
    private fun joinGame(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException
        val arenaId = StringArgumentType.getString(ctx, "arena")
        
        return if (PropHuntManager.joinGame(player, arenaId)) 1 else 0
    }
    
    private fun leaveGame(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException
        return if (PropHuntManager.leaveGame(player)) 1 else 0
    }
    
    private fun listGames(ctx: CommandContext<CommandSourceStack>): Int {
        val games = PropHuntManager.listGames()
        
        if (games.isEmpty()) {
            ctx.source.sendSuccess({ Component.literal("§7No active Prop Hunt games.") }, false)
        } else {
            ctx.source.sendSuccess({ Component.literal("§6§l═══════ ACTIVE GAMES ═══════") }, false)
            for (game in games) {
                val status = when (game.state) {
                    GameState.WAITING -> "§e⏳ Waiting"
                    GameState.STARTING -> "§e⏱ Starting"
                    GameState.HIDING -> "§a🎭 Hiding"
                    GameState.SEEKING -> "§c🔍 Seeking"
                    GameState.ROUND_END -> "§6🏆 Round End"
                    GameState.ENDED -> "§8✖ Ended"
                }
                ctx.source.sendSuccess({ 
                    Component.literal("§7• §e${game.arenaId}§7: $status §7(${game.players.size}/${game.settings.maxPlayers})") 
                }, false)
            }
            ctx.source.sendSuccess({ Component.literal("§6§l════════════════════════════") }, false)
        }
        return 1
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // PROP IN-GAME COMMANDS
    // ════════════════════════════════════════════════════════════════════════
    
    private fun toggleLock(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException
        val game = PropHuntManager.getPlayerGame(player)
        
        if (game == null) {
            ctx.source.sendFailure(Component.literal("§cYou're not in a Prop Hunt game!"))
            return 0
        }
        
        val playerData = game.players[player.uuid]
        if (playerData?.team != PropHuntTeam.PROP) {
            ctx.source.sendFailure(Component.literal("§cOnly props can use this command!"))
            return 0
        }
        
        PropDisguiseManager.toggleRotationLock(player, game)
        return 1
    }
    
    private fun toggleFreeze(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException
        val game = PropHuntManager.getPlayerGame(player)
        
        if (game == null) {
            ctx.source.sendFailure(Component.literal("§cYou're not in a Prop Hunt game!"))
            return 0
        }
        
        val playerData = game.players[player.uuid]
        if (playerData?.team != PropHuntTeam.PROP) {
            ctx.source.sendFailure(Component.literal("§cOnly props can use this command!"))
            return 0
        }
        
        PropDisguiseManager.toggleFreeze(player, game)
        return 1
    }
    
    private fun performTaunt(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException
        val game = PropHuntManager.getPlayerGame(player)
        
        if (game == null) {
            ctx.source.sendFailure(Component.literal("§cYou're not in a Prop Hunt game!"))
            return 0
        }
        
        val playerData = game.players[player.uuid]
        if (playerData?.team != PropHuntTeam.PROP) {
            ctx.source.sendFailure(Component.literal("§cOnly props can use this command!"))
            return 0
        }
        
        PropDisguiseManager.performTaunt(player, game)
        return 1
    }
    
    private fun changeDisguise(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException
        val game = PropHuntManager.getPlayerGame(player)
        val typeName = StringArgumentType.getString(ctx, "type")
        
        if (game == null) {
            ctx.source.sendFailure(Component.literal("§cYou're not in a Prop Hunt game!"))
            return 0
        }
        
        // Only allow during hiding phase
        if (game.state != GameState.HIDING) {
            ctx.source.sendFailure(Component.literal("§cYou can only change disguise during the hiding phase!"))
            return 0
        }
        
        val playerData = game.players[player.uuid]
        if (playerData?.team != PropHuntTeam.PROP) {
            ctx.source.sendFailure(Component.literal("§cOnly props can use this command!"))
            return 0
        }
        
        return if (PropDisguiseManager.chooseDisguise(player, typeName, game)) 1 else 0
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // HUNTER IN-GAME COMMANDS
    // ════════════════════════════════════════════════════════════════════════
    
    private fun useScan(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException
        val game = PropHuntManager.getPlayerGame(player)
        
        if (game == null) {
            ctx.source.sendFailure(Component.literal("§cYou're not in a Prop Hunt game!"))
            return 0
        }
        
        val playerData = game.players[player.uuid]
        if (playerData?.team != PropHuntTeam.HUNTER) {
            ctx.source.sendFailure(Component.literal("§cOnly hunters can use this command!"))
            return 0
        }
        
        return if (HunterAbilityManager.useScanner(player, game)) 1 else 0
    }
    
    private fun useTracker(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException
        val game = PropHuntManager.getPlayerGame(player)
        
        if (game == null) {
            ctx.source.sendFailure(Component.literal("§cYou're not in a Prop Hunt game!"))
            return 0
        }
        
        val playerData = game.players[player.uuid]
        if (playerData?.team != PropHuntTeam.HUNTER) {
            ctx.source.sendFailure(Component.literal("§cOnly hunters can use this command!"))
            return 0
        }
        
        return if (HunterAbilityManager.useTracker(player, game)) 1 else 0
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // ADMIN COMMANDS
    // ════════════════════════════════════════════════════════════════════════
    
    private fun forceStart(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException
        val game = PropHuntManager.getPlayerGame(player)
        
        if (game == null) {
            ctx.source.sendFailure(Component.literal("§cYou're not in a Prop Hunt game!"))
            return 0
        }
        
        if (game.state != GameState.WAITING && game.state != GameState.STARTING) {
            ctx.source.sendFailure(Component.literal("§cGame has already started!"))
            return 0
        }
        
        if (game.players.size < 2) {
            ctx.source.sendFailure(Component.literal("§cNeed at least 2 players to start!"))
            return 0
        }
        
        PropHuntManager.startRound(game)
        ctx.source.sendSuccess({ Component.literal("§a[PropHunt] §7Game force-started!") }, true)
        return 1
    }
    
    private fun stopCurrentGame(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException
        val game = PropHuntManager.getPlayerGame(player)
        
        if (game == null) {
            ctx.source.sendFailure(Component.literal("§cYou're not in a Prop Hunt game!"))
            return 0
        }
        
        PropHuntManager.endGame(game.arenaId, "Stopped by admin")
        ctx.source.sendSuccess({ Component.literal("§a[PropHunt] §7Game stopped!") }, true)
        return 1
    }
    
    private fun stopGame(ctx: CommandContext<CommandSourceStack>): Int {
        val arenaId = StringArgumentType.getString(ctx, "arena")
        PropHuntManager.endGame(arenaId, "Stopped by admin")
        ctx.source.sendSuccess({ Component.literal("§a[PropHunt] §7Game in arena '$arenaId' stopped!") }, true)
        return 1
    }
    
    private fun skipPhase(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException
        val game = PropHuntManager.getPlayerGame(player)
        
        if (game == null) {
            ctx.source.sendFailure(Component.literal("§cYou're not in a Prop Hunt game!"))
            return 0
        }
        
        when (game.state) {
            GameState.HIDING -> {
                game.hideTimeTicks = 0
                ctx.source.sendSuccess({ Component.literal("§a[PropHunt] §7Skipping to seeking phase!") }, true)
            }
            GameState.SEEKING -> {
                PropHuntManager.endRound(game, PropHuntTeam.NONE, "Phase skipped by admin")
                ctx.source.sendSuccess({ Component.literal("§a[PropHunt] §7Round ended!") }, true)
            }
            else -> {
                ctx.source.sendFailure(Component.literal("§cCannot skip this phase!"))
                return 0
            }
        }
        return 1
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // ARENA COMMANDS
    // ════════════════════════════════════════════════════════════════════════
    
    private fun createArenaHere(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException
        val arenaId = StringArgumentType.getString(ctx, "id")
        val level = ctx.source.level
        
        val arena = ArenaManager.getOrCreateArena(arenaId, level, player.blockPosition())
        if (arena != null) {
            ctx.source.sendSuccess({ Component.literal("§a[PropHunt] §7Arena '$arenaId' created at your location!") }, true)
            return 1
        }
        return 0
    }
    
    private fun createArenaWithPreset(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException
        val arenaId = StringArgumentType.getString(ctx, "id")
        val presetName = StringArgumentType.getString(ctx, "preset")
        
        ctx.source.sendSuccess({ 
            Component.literal("§a[PropHunt] §7Arena '$arenaId' created with preset '$presetName'!") 
        }, true)
        return 1
    }
    
    private fun setCustomArena(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException
        val arenaId = StringArgumentType.getString(ctx, "id")
        val pos1 = BlockPosArgument.getLoadedBlockPos(ctx, "pos1")
        val pos2 = BlockPosArgument.getLoadedBlockPos(ctx, "pos2")
        val lobby = BlockPosArgument.getLoadedBlockPos(ctx, "lobby")
        val level = ctx.source.level
        
        ArenaManager.setCustomArena(arenaId, level, pos1, pos2, lobby)
        ctx.source.sendSuccess({ Component.literal("§a[PropHunt] §7Custom arena '$arenaId' set!") }, true)
        return 1
    }
    
    private fun removeArena(ctx: CommandContext<CommandSourceStack>): Int {
        val arenaId = StringArgumentType.getString(ctx, "id")
        
        if (ArenaManager.removeArena(arenaId)) {
            ctx.source.sendSuccess({ Component.literal("§a[PropHunt] §7Arena '$arenaId' removed!") }, true)
            return 1
        } else {
            ctx.source.sendFailure(Component.literal("§cArena '$arenaId' not found!"))
            return 0
        }
    }
    
    private fun listArenas(ctx: CommandContext<CommandSourceStack>): Int {
        val arenas = ArenaManager.listArenas()
        
        if (arenas.isEmpty()) {
            ctx.source.sendSuccess({ Component.literal("§7No arenas defined.") }, false)
        } else {
            ctx.source.sendSuccess({ Component.literal("§6§l═══════ ARENAS ═══════") }, false)
            for (arenaId in arenas) {
                val arena = ArenaManager.getArena(arenaId)
                ctx.source.sendSuccess({ Component.literal("§7• §e$arenaId §7- ${arena?.getDescription() ?: "Unknown"}") }, false)
            }
            ctx.source.sendSuccess({ Component.literal("§6§l═══════════════════════") }, false)
        }
        return 1
    }
    
    private fun teleportToArena(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException
        val arenaId = StringArgumentType.getString(ctx, "id")
        
        val arena = ArenaManager.getArena(arenaId)
        if (arena == null) {
            ctx.source.sendFailure(Component.literal("§cArena '$arenaId' not found!"))
            return 0
        }
        
        ArenaManager.teleportToLobby(player, arena)
        return 1
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // SETTINGS COMMANDS
    // ════════════════════════════════════════════════════════════════════════
    
    private fun setRounds(ctx: CommandContext<CommandSourceStack>): Int {
        val count = IntegerArgumentType.getInteger(ctx, "count")
        PropHuntManager.config.defaultSettings.maxRounds = count
        ctx.source.sendSuccess({ Component.literal("§a[PropHunt] §7Max rounds set to §e$count") }, true)
        return 1
    }
    
    private fun setHideTime(ctx: CommandContext<CommandSourceStack>): Int {
        val seconds = IntegerArgumentType.getInteger(ctx, "seconds")
        PropHuntManager.config.defaultSettings.hideTimeSeconds = seconds
        ctx.source.sendSuccess({ Component.literal("§a[PropHunt] §7Hide time set to §e${seconds}s") }, true)
        return 1
    }
    
    private fun setRoundTime(ctx: CommandContext<CommandSourceStack>): Int {
        val seconds = IntegerArgumentType.getInteger(ctx, "seconds")
        PropHuntManager.config.defaultSettings.roundTimeSeconds = seconds
        ctx.source.sendSuccess({ Component.literal("§a[PropHunt] §7Round time set to §e${seconds}s") }, true)
        return 1
    }
    
    private fun setHunterCount(ctx: CommandContext<CommandSourceStack>): Int {
        val min = IntegerArgumentType.getInteger(ctx, "min")
        val max = IntegerArgumentType.getInteger(ctx, "max")
        PropHuntManager.config.defaultSettings.minHunters = min
        PropHuntManager.config.defaultSettings.maxHunters = max
        ctx.source.sendSuccess({ Component.literal("§a[PropHunt] §7Hunter count set to §e$min-$max") }, true)
        return 1
    }
    
    private fun setHints(ctx: CommandContext<CommandSourceStack>, enabled: Boolean): Int {
        PropHuntManager.config.defaultSettings.hunterHintsEnabled = enabled
        val status = if (enabled) "§aenabled" else "§cdisabled"
        ctx.source.sendSuccess({ Component.literal("§a[PropHunt] §7Hunter hints $status") }, true)
        return 1
    }
    
    private fun showSettings(ctx: CommandContext<CommandSourceStack>): Int {
        val settings = PropHuntManager.config.defaultSettings
        ctx.source.sendSuccess({ Component.literal("""
            §6§l═══════ PROP HUNT SETTINGS ═══════
            §7Max Rounds: §e${settings.maxRounds}
            §7Hide Time: §e${settings.hideTimeSeconds}s
            §7Round Time: §e${settings.roundTimeSeconds}s
            §7Hunters: §e${settings.minHunters}-${settings.maxHunters}
            §7Hunter Hints: ${if (settings.hunterHintsEnabled) "§aOn" else "§cOff"}
            §7Taunt Cooldown: §e${settings.tauntCooldownSeconds}s
            §7Scanner Cooldown: §e${settings.scannerCooldownSeconds}s
            §7Tracker Cooldown: §e${settings.trackerCooldownSeconds}s
            §7Stun Grenades: §e${settings.stunGrenadesPerRound}/round
            §6§l════════════════════════════════════
        """.trimIndent()) }, false)
        return 1
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // STATISTICS COMMANDS
    // ════════════════════════════════════════════════════════════════════════
    
    private fun showStats(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException
        val stats = PropHuntManager.statistics
        
        ctx.source.sendSuccess({ Component.literal("""
            §6§l═══════ YOUR PROP HUNT STATS ═══════
            §aProp Wins: §f${stats.getPropWins(player.uuid)}
            §cHunter Wins: §f${stats.getHunterWins(player.uuid)}
            §eProps Found: §f${stats.getPropsFound(player.uuid)}
            §bBest Survival: §f${stats.getBestSurvivalTime(player.uuid) / 20}s
            §6§l═══════════════════════════════════════
        """.trimIndent()) }, false)
        return 1
    }
    
    private fun showPlayerStats(ctx: CommandContext<CommandSourceStack>): Int {
        val target = EntityArgument.getPlayer(ctx, "player")
        val stats = PropHuntManager.statistics
        
        ctx.source.sendSuccess({ Component.literal("""
            §6§l═══════ ${target.name.string}'s PROP HUNT STATS ═══════
            §aProp Wins: §f${stats.getPropWins(target.uuid)}
            §cHunter Wins: §f${stats.getHunterWins(target.uuid)}
            §eProps Found: §f${stats.getPropsFound(target.uuid)}
            §bBest Survival: §f${stats.getBestSurvivalTime(target.uuid) / 20}s
            §6§l════════════════════════════════════════════════════
        """.trimIndent()) }, false)
        return 1
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // EVENT HANDLERS
    // ════════════════════════════════════════════════════════════════════════
    
    private fun registerEventHandlers() {
        // Handle player attacks
        AttackEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
            if (world.isClientSide) return@register net.minecraft.world.InteractionResult.PASS
            if (player !is ServerPlayer) return@register net.minecraft.world.InteractionResult.PASS
            
            val game = PropHuntManager.getPlayerGame(player)
            if (game != null && game.state == GameState.SEEKING) {
                val playerData = game.players[player.uuid]
                if (playerData?.team == PropHuntTeam.HUNTER) {
                    // Process hunter attack
                    HunterAbilityManager.processHunterAttack(player, entity, game)
                }
            }
            
            net.minecraft.world.InteractionResult.PASS
        }
        
        // Handle item use (for prop abilities)
        UseItemCallback.EVENT.register { player, world, hand ->
            if (world.isClientSide) return@register InteractionResult.PASS
            if (player !is ServerPlayer) return@register InteractionResult.PASS
            
            val game = PropHuntManager.getPlayerGame(player)
            if (game != null) {
                val item = player.getItemInHand(hand)
                val playerData = game.players[player.uuid]
                
                // Prop items
                if (playerData?.team == PropHuntTeam.PROP) {
                    when (item.item) {
                        Items.COMPASS -> {
                            PropDisguiseManager.toggleRotationLock(player, game)
                            return@register InteractionResult.SUCCESS
                        }
                        Items.BLUE_ICE -> {
                            PropDisguiseManager.toggleFreeze(player, game)
                            return@register InteractionResult.SUCCESS
                        }
                        Items.GOAT_HORN -> {
                            PropDisguiseManager.performTaunt(player, game)
                            return@register InteractionResult.SUCCESS
                        }
                    }
                }
                
                // Hunter items
                if (playerData?.team == PropHuntTeam.HUNTER) {
                    when (item.item) {
                        Items.ECHO_SHARD -> {
                            HunterAbilityManager.useScanner(player, game)
                            return@register InteractionResult.SUCCESS
                        }
                        Items.SPECTRAL_ARROW -> {
                            HunterAbilityManager.useTracker(player, game)
                            return@register InteractionResult.SUCCESS
                        }
                    }
                }
            }
            
            InteractionResult.PASS
        }
        
        // Handle player disconnect
        ServerPlayConnectionEvents.DISCONNECT.register { handler, server ->
            PropHuntManager.onPlayerDisconnect(handler.player)
        }
    }
}
