package com.qc.aeonis.companion

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

object CompanionCommands {
    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            registerCommands(dispatcher)
        }
    }

    private fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("comp")
                .requires { it.player != null }
                .then(Commands.literal("spawn").executes { spawn(it) })
                .then(Commands.literal("comehere").executes { comeHere(it) })
                .then(Commands.literal("stats").executes { stats(it) })
                .then(Commands.literal("dismiss").executes { dismiss(it) })
                .executes { help(it) }
        )
    }

    private fun spawn(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException as ServerPlayer
        val bot = CompanionBotManager.spawnOrReplace(player)
        return if (bot != null) {
            player.sendSystemMessage(Component.literal("§a[Companion] §7Helper bot spawned and bound to you."))
            1
        } else {
            player.sendSystemMessage(Component.literal("§c[Companion] §7Failed to spawn helper bot."))
            0
        }
    }

    private fun comeHere(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException as ServerPlayer
        return if (CompanionBotManager.recall(player)) {
            player.sendSystemMessage(Component.literal("§a[Companion] §7Come here command sent."))
            1
        } else {
            player.sendSystemMessage(Component.literal("§c[Companion] §7No helper bot found. Use /comp spawn."))
            0
        }
    }

    private fun stats(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException as ServerPlayer
        val bot = CompanionBotManager.getBot(player)
        if (bot == null) {
            player.sendSystemMessage(Component.literal("§c[Companion] §7No helper bot found. Use /comp spawn."))
            return 0
        }

        player.sendSystemMessage(Component.literal("§6§l═══════ COMPANION STATS ═══════"))
        player.sendSystemMessage(Component.literal("§7State: §e${bot.currentState.name}"))
        player.sendSystemMessage(Component.literal("§7Health: §c${"%.1f".format(bot.health)}§7/§c${"%.1f".format(bot.maxHealth)}"))
        player.sendSystemMessage(Component.literal("§7Strength: §a${bot.getStrengthSummary()}"))
        return 1
    }

    private fun dismiss(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException as ServerPlayer
        return if (CompanionBotManager.dismiss(player)) {
            player.sendSystemMessage(Component.literal("§e[Companion] §7Helper bot dismissed."))
            1
        } else {
            player.sendSystemMessage(Component.literal("§c[Companion] §7No helper bot to dismiss."))
            0
        }
    }

    private fun help(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.playerOrException as ServerPlayer
        player.sendSystemMessage(Component.literal("§6§lCompanion Commands"))
        player.sendSystemMessage(Component.literal("§e/comp spawn §7- Spawn or replace your helper bot"))
        player.sendSystemMessage(Component.literal("§e/comp comehere §7- Recall helper bot (run <=500 blocks, TP if farther)"))
        player.sendSystemMessage(Component.literal("§e/comp stats §7- Show companion strength and status"))
        player.sendSystemMessage(Component.literal("§e/comp dismiss §7- Remove your helper bot"))
        return 1
    }
}
