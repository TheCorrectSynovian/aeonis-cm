package com.qc.aeonis.item

import com.qc.aeonis.block.AeonisBlocks
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
import net.minecraft.world.level.block.CampfireBlock
import net.minecraft.world.level.block.CandleBlock
import net.minecraft.world.level.block.CandleCakeBlock
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.level.portal.PortalShape
import net.minecraft.server.level.ServerPlayer

class AncardLighterItem(properties: Properties) : Item(properties) {
    override fun useOn(context: UseOnContext): InteractionResult {
        val level = context.level
        val clickedPos = context.clickedPos
        val state = level.getBlockState(clickedPos)
        val player = context.player

        // Light campfires, candles, candle cakes
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

        // Try to light an Ancard portal
        if (tryActivateAncardPortal(level, placePos, axis)) {
            if (player != null) {
                context.itemInHand.hurtAndBreak(1, player, context.hand.asEquipmentSlot())
            }
            level.playSound(player, placePos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 0.8F + level.random.nextFloat() * 0.2F)
            level.gameEvent(player, GameEvent.BLOCK_PLACE, placePos)
            return InteractionResult.SUCCESS
        }

        // Try to light a Nether portal
        if (tryActivateNetherPortal(level, placePos, axis)) {
            if (player != null) {
                context.itemInHand.hurtAndBreak(1, player, context.hand.asEquipmentSlot())
            }
            level.playSound(player, placePos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 0.8F + level.random.nextFloat() * 0.2F)
            level.gameEvent(player, GameEvent.BLOCK_PLACE, placePos)
            return InteractionResult.SUCCESS
        }

        // Place permanent flame anywhere (the block survives on any surface)
        if (level.getBlockState(placePos).isAir) {
            level.playSound(player, placePos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, level.random.nextFloat() * 0.4F + 0.8F)
            level.setBlock(placePos, AeonisBlocks.PERMANENT_FLAME.defaultBlockState(), 11)
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

    private fun tryActivateAncardPortal(level: Level, pos: BlockPos, axis: Direction.Axis): Boolean {
        if (level.isClientSide) return false
        val shape = AncardPortalShape.findEmptyPortalShape(level, pos, axis)
        if (shape.isPresent) {
            shape.get().createPortalBlocks(level)
            return true
        }
        return false
    }

    private fun tryActivateNetherPortal(level: Level, pos: BlockPos, axis: Direction.Axis): Boolean {
        if (level.isClientSide) return false
        // Nether portals only work in the Overworld or the Nether
        if (level.dimension() != Level.OVERWORLD && level.dimension() != Level.NETHER) return false
        val shape = PortalShape.findEmptyPortalShape(level, pos, axis)
        if (shape.isPresent) {
            shape.get().createPortalBlocks(level)
            return true
        }
        return false
    }
}
