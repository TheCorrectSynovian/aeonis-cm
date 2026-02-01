package com.qc.aeonis.llm;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.qc.aeonis.llm.config.LlmConfigStorage;
import com.qc.aeonis.llm.network.LlmNetworking;
import com.qc.aeonis.llm.task.BuildTask;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command registration for LLM features.
 * 
 * Commands:
 * - /ai llm spawn       - Opens config GUI, spawns bot
 * - /ai llm despawn     - Removes the bot
 * - /ai llm follow <player>
 * - /ai llm walkTo <x y z>
 * - /ai llm build <preset>
 * - /ai llm stop
 * - /ai llm status
 */
public class LlmCommands {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("aeonis-llm-commands");
    
    /**
     * Register all LLM commands (call from main mod command registration)
     */
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
        });
    }
    
    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ai")
            .then(Commands.literal("llm")
                // /ai llm spawn - Opens GUI for config and spawns bot
                .then(Commands.literal("spawn")
                    .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) || isSinglePlayer(source))
                    .executes(LlmCommands::openConfigGui)
                )
                
                // /ai llm despawn - Removes the bot
                .then(Commands.literal("despawn")
                    .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) || isSinglePlayer(source))
                    .executes(LlmCommands::despawnBot)
                )
                
                // /ai llm follow <player>
                .then(Commands.literal("follow")
                    .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                            return followPlayer(ctx, target);
                        })
                    )
                )
                
                // /ai llm walkTo <x y z>
                .then(Commands.literal("walkTo")
                    .then(Commands.argument("pos", Vec3Argument.vec3())
                        .executes(ctx -> {
                            Vec3 pos = Vec3Argument.getVec3(ctx, "pos");
                            return walkTo(ctx, pos);
                        })
                    )
                )
                
                // /ai llm build <preset>
                .then(Commands.literal("build")
                    .then(Commands.argument("preset", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (BuildTask.Preset preset : BuildTask.Preset.values()) {
                                builder.suggest(preset.name().toLowerCase());
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String preset = StringArgumentType.getString(ctx, "preset");
                            return build(ctx, preset);
                        })
                    )
                )
                
                // /ai llm stop
                .then(Commands.literal("stop")
                    .executes(LlmCommands::stopTask)
                )
                
                // /ai llm status
                .then(Commands.literal("status")
                    .executes(LlmCommands::showStatus)
                )
                
                // /ai llm config - Opens config GUI
                .then(Commands.literal("config")
                    .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) || isSinglePlayer(source))
                    .executes(LlmCommands::openConfigGui)
                )
            )
        );
        
        LOGGER.info("LLM commands registered");
    }
    
    private static boolean isSinglePlayer(CommandSourceStack source) {
        var server = source.getServer();
        return server != null && server.isSingleplayer();
    }
    
    // ================ COMMAND HANDLERS ================
    
    private static ServerLevel getServerLevel(ServerPlayer player) {
        return (ServerLevel) player.level();
    }
    
    private static int openConfigGui(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }
        
        // Send packet to open GUI on client
        LlmNetworking.sendOpenGui(player);
        
        return 1;
    }
    
    private static int despawnBot(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }
        
        AeonisAssistant assistant = AeonisAssistant.getInstance(getServerLevel(player));
        if (assistant != null) {
            assistant.despawn();
            LlmConfigStorage storage = LlmConfigStorage.getInstance();
            if (storage != null) {
                storage.setBotEnabled(false, null);
            }
            ctx.getSource().sendSuccess(() -> Component.literal("§aAeonis has been despawned."), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.literal("Aeonis is not spawned in this dimension."));
            return 0;
        }
    }
    
    private static int followPlayer(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        
        AeonisAssistant assistant = AeonisAssistant.getInstance(getServerLevel(player));
        if (assistant == null) {
            ctx.getSource().sendFailure(Component.literal("Aeonis is not spawned. Use /ai llm spawn first."));
            return 0;
        }
        
        assistant.getAeonisBrain().follow(target);
        return 1;
    }
    
    private static int walkTo(CommandContext<CommandSourceStack> ctx, Vec3 pos) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        
        AeonisAssistant assistant = AeonisAssistant.getInstance(getServerLevel(player));
        if (assistant == null) {
            ctx.getSource().sendFailure(Component.literal("Aeonis is not spawned. Use /ai llm spawn first."));
            return 0;
        }
        
        assistant.getAeonisBrain().navigateTo(pos);
        return 1;
    }
    
    private static int build(CommandContext<CommandSourceStack> ctx, String presetName) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        
        AeonisAssistant assistant = AeonisAssistant.getInstance(getServerLevel(player));
        if (assistant == null) {
            ctx.getSource().sendFailure(Component.literal("Aeonis is not spawned. Use /ai llm spawn first."));
            return 0;
        }
        
        BuildTask.Preset preset = BuildTask.Preset.fromName(presetName);
        if (preset == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown preset: " + presetName));
            ctx.getSource().sendFailure(Component.literal("§7Available: " + String.join(", ", BuildTask.getPresetNames())));
            return 0;
        }
        
        assistant.getAeonisBrain().build(preset);
        return 1;
    }
    
    private static int stopTask(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        
        AeonisAssistant assistant = AeonisAssistant.getInstance(getServerLevel(player));
        if (assistant == null) {
            ctx.getSource().sendFailure(Component.literal("Aeonis is not spawned."));
            return 0;
        }
        
        assistant.getAeonisBrain().stopCurrentTask();
        ctx.getSource().sendSuccess(() -> Component.literal("§aAeonis stopped."), false);
        return 1;
    }
    
    private static int showStatus(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        
        AeonisAssistant assistant = AeonisAssistant.getInstance(getServerLevel(player));
        if (assistant == null) {
            ctx.getSource().sendSuccess(() -> Component.literal("§7Aeonis is not spawned in this dimension."), false);
            return 1;
        }
        
        AeonisBrain brain = assistant.getAeonisBrain();
        
        ctx.getSource().sendSuccess(() -> Component.literal("§b=== Aeonis Status ==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7State: §f" + brain.getCurrentState()), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Task: §f" + brain.getStatusMessage()), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Queued tasks: §f" + brain.getQueuedTaskCount()), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Queued chats: §f" + brain.getQueuedChatCount()), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Position: §f" + 
            String.format("%.1f, %.1f, %.1f", assistant.getX(), assistant.getY(), assistant.getZ())), false);
        
        return 1;
    }
}
