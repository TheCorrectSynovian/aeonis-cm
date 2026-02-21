package com.qc.aeonis.item

import com.qc.aeonis.dimension.AncardPortalShape
import net.minecraft.advancements.CriteriaTriggers
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseFireBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CampfireBlock
import net.minecraft.world.level.block.CandleBlock
import net.minecraft.world.level.block.CandleCakeBlock
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.server.level.ServerPlayer

class AncardLighterItem(properties: Properties) : Item(properties) {
    override fun useOn(context: UseOnContext): InteractionResult {
        val level = context.level
        val clickedPos = context.clickedPos
        val state = level.getBlockState(clickedPos)
        val player = context.player

        if (CampfireBlock.canLight(state) || CandleBlock.canLight(state) || CandleCakeBlock.canLight(state)) {
            level.playSound(player, clickedPos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, level.random.nextFloat() * 0.4F + 0.8F)
            level.setBlock(clickedPos, state.setValue(BlockStateProperties.LIT, true), 11)
            level.gameEvent(player, GameEvent.BLOCK_CHANGE, clickedPos)
            if (player != null) {
                context.itemInHand.hurtAndBreak(1, player, context.hand.asEquipmentSlot())
            }
            return InteractionResult.SUCCESS
        }

        val placePos = clickedPos.relative(context.clickedFace)
        val axis = if (context.clickedFace.axis.isHorizontal) {
            context.clickedFace.axis
        } else {
            context.horizontalDirection.axis
        }

        if (tryActivate(level, placePos, axis)) {
            if (player != null) {
                context.itemInHand.hurtAndBreak(1, player, context.hand.asEquipmentSlot())
            }
            level.playSound(player, placePos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 0.8F + level.random.nextFloat() * 0.2F)
            level.gameEvent(player, GameEvent.BLOCK_PLACE, placePos)
            return InteractionResult.SUCCESS
        }

        if (level.getBlockState(placePos).isAir && (Blocks.SOUL_FIRE.defaultBlockState().canSurvive(level, placePos) || BaseFireBlock.canBePlacedAt(level, placePos, context.horizontalDirection))) {
            level.playSound(player, placePos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, level.random.nextFloat() * 0.4F + 0.8F)
            level.setBlock(placePos, Blocks.SOUL_FIRE.defaultBlockState(), 11)
            level.gameEvent(player, GameEvent.BLOCK_PLACE, placePos)
            if (player is ServerPlayer) {
                CriteriaTriggers.PLACED_BLOCK.trigger(player, placePos, context.itemInHand)
                context.itemInHand.hurtAndBreak(1, player, context.hand.asEquipmentSlot())
            } else if (player != null) {
                context.itemInHand.hurtAndBreak(1, player, context.hand.asEquipmentSlot())
            }
            return InteractionResult.SUCCESS
        }

        return InteractionResult.FAIL
    }

    private fun tryActivate(level: Level, pos: BlockPos, axis: Direction.Axis): Boolean {
        if (level.isClientSide) return false
        val shape = AncardPortalShape.findEmptyPortalShape(level, pos, axis)
        if (shape.isPresent) {
            shape.get().createPortalBlocks(level)
            return true
        }
        return false
    }
}
