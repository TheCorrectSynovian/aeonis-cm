package com.qc.aeonis.item

import com.qc.aeonis.companion.CompanionBotManager
import com.qc.aeonis.entity.CompanionBotEntity
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level

/**
 * Rhistel — a companion whistle that cycles the companion bot through
 * forced behaviour modes.
 *
 * Mode cycle:  ATTACK → FOLLOW → RUN_AWAY → AUTO → (repeat)
 *
 * ATTACK / FOLLOW / RUN_AWAY override the default AI for 2 minutes (2 400 ticks).
 * AUTO restores normal decision-making immediately.
 */
class RhistelItem(properties: Properties) : Item(properties) {

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        if (level.isClientSide) return InteractionResult.SUCCESS

        val serverPlayer = player as? ServerPlayer ?: return InteractionResult.PASS
        val bot = CompanionBotManager.getBot(serverPlayer)

        if (bot == null) {
            serverPlayer.sendSystemMessage(
                Component.literal("§c[Rhistel] §7No companion found. Spawn one with §e/comp spawn§7.")
            )
            return InteractionResult.FAIL
        }

        // Cycle to next mode
        val currentMode = bot.forcedMode
        val nextMode = when (currentMode) {
            CompanionBotEntity.ForcedMode.NONE  -> CompanionBotEntity.ForcedMode.ATTACK
            CompanionBotEntity.ForcedMode.ATTACK -> CompanionBotEntity.ForcedMode.FOLLOW
            CompanionBotEntity.ForcedMode.FOLLOW -> CompanionBotEntity.ForcedMode.RUN_AWAY
            CompanionBotEntity.ForcedMode.RUN_AWAY -> CompanionBotEntity.ForcedMode.NONE
        }

        bot.setForcedMode(nextMode)

        // Feedback
        val (label, color) = when (nextMode) {
            CompanionBotEntity.ForcedMode.ATTACK  -> "ATTACK"  to "§c"
            CompanionBotEntity.ForcedMode.FOLLOW   -> "FOLLOW"  to "§a"
            CompanionBotEntity.ForcedMode.RUN_AWAY -> "RUN AWAY" to "§e"
            CompanionBotEntity.ForcedMode.NONE     -> "AUTO"    to "§b"
        }

        serverPlayer.sendSystemMessage(
            Component.literal("§6[Rhistel] §7Mode set to $color$label")
        )

        // Whistle sound
        level.playSound(
            null,
            player.blockPosition(),
            SoundEvents.NOTE_BLOCK_BELL.value(),
            SoundSource.PLAYERS,
            1.0f,
            1.4f
        )

        // Cooldown so you don't accidentally multi-click (10 ticks = 0.5s)
        serverPlayer.cooldowns.addCooldown(player.getItemInHand(hand), 10)

        return InteractionResult.SUCCESS
    }

    override fun appendHoverText(
        stack: ItemStack,
        context: TooltipContext,
        tooltipDisplay: net.minecraft.world.item.component.TooltipDisplay,
        consumer: java.util.function.Consumer<Component>,
        flag: TooltipFlag
    ) {
        consumer.accept(Component.literal("§7Right-click to cycle companion mode"))
        consumer.accept(Component.literal("§8ATTACK → FOLLOW → RUN AWAY → AUTO"))
    }
}
