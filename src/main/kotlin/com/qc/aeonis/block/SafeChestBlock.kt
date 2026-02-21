package com.qc.aeonis.block

import com.mojang.serialization.MapCodec
import com.qc.aeonis.block.entity.SafeChestBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.stats.Stats
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.piglin.PiglinAi
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.phys.BlockHitResult
import com.qc.aeonis.block.entity.AeonisBlockEntities

/**
 * A tough container block (Safe Chest) with very high blast resistance.
 * Supports single, double (2 wide), and triple (3 wide) configurations.
 * Opens with a drawer-style animation.
 */
class SafeChestBlock(properties: BlockBehaviour.Properties) : BaseEntityBlock(properties) {

    companion object {
        val CODEC: MapCodec<SafeChestBlock> = simpleCodec(::SafeChestBlock)
        val FACING: EnumProperty<Direction> = BlockStateProperties.HORIZONTAL_FACING
        val CHEST_TYPE: EnumProperty<SafeChestType> = EnumProperty.create("type", SafeChestType::class.java)
    }

    override fun codec(): MapCodec<out BaseEntityBlock> = CODEC

    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(CHEST_TYPE, SafeChestType.SINGLE)
        )
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
        SafeChestBlockEntity(pos, state)

    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        return if (!level.isClientSide) {
            createTickerHelper(type, AeonisBlockEntities.SAFE_CHEST) { world, pos, blockState, entity ->
                entity.tick(world, pos, blockState)
            }
        } else null
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hit: BlockHitResult
    ): InteractionResult {
        if (level is ServerLevel) {
            val controllerPos = getControllerPos(state, pos)
            val controller = level.getBlockEntity(controllerPos) as? SafeChestBlockEntity
            if (controller != null) {
                player.openMenu(controller)
                player.awardStat(Stats.OPEN_CHEST)
                PiglinAi.angerNearbyPiglins(level, player, true)
            }
        }
        return InteractionResult.SUCCESS
    }

    override fun hasAnalogOutputSignal(state: BlockState): Boolean = true

    override fun getAnalogOutputSignal(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        direction: Direction
    ): Int {
        val controllerPos = getControllerPos(state, pos)
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(controllerPos))
    }

    override fun rotate(state: BlockState, rotation: Rotation): BlockState =
        state.setValue(FACING, rotation.rotate(state.getValue(FACING)))

    override fun mirror(state: BlockState, mirror: Mirror): BlockState =
        state.rotate(mirror.getRotation(state.getValue(FACING)))

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING, CHEST_TYPE)
    }

    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState {
        val facing = ctx.horizontalDirection.opposite
        return defaultBlockState().setValue(FACING, facing).setValue(CHEST_TYPE, SafeChestType.SINGLE)
    }

    override fun setPlacedBy(
        level: Level,
        pos: BlockPos,
        state: BlockState,
        placer: LivingEntity?,
        stack: ItemStack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack)
        if (level.isClientSide) return
        val facing = state.getValue(FACING)
        tryMergeWithNeighbors(level, pos, facing)
    }

    /**
     * Called after a block is removed. Reconfigure remaining connected chests.
     * Item drops are handled by SafeChestBlockEntity.preRemoveSideEffects().
     */
    override fun affectNeighborsAfterRemoval(
        state: BlockState,
        level: ServerLevel,
        pos: BlockPos,
        movedByPiston: Boolean
    ) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston)

        val chestType = state.getValue(CHEST_TYPE)
        val facing = state.getValue(FACING)
        val rightDir = getConnectDirection(facing)

        when (chestType) {
            SafeChestType.LEFT -> {
                val rightPos = pos.relative(rightDir)
                setSingleIfSafe(level, rightPos)
            }
            SafeChestType.RIGHT -> {
                val leftPos = pos.relative(rightDir.opposite)
                setSingleIfSafe(level, leftPos)
            }
            SafeChestType.TRIPLE_LEFT -> {
                val midPos = pos.relative(rightDir)
                val rightPos = pos.relative(rightDir, 2)
                setTypeIfSafe(level, midPos, SafeChestType.LEFT)
                setTypeIfSafe(level, rightPos, SafeChestType.RIGHT)
            }
            SafeChestType.TRIPLE_MIDDLE -> {
                val leftPos = pos.relative(rightDir.opposite)
                val rightPos = pos.relative(rightDir)
                setSingleIfSafe(level, leftPos)
                setSingleIfSafe(level, rightPos)
            }
            SafeChestType.TRIPLE_RIGHT -> {
                val leftPos = pos.relative(rightDir.opposite, 2)
                val midPos = pos.relative(rightDir.opposite)
                setTypeIfSafe(level, leftPos, SafeChestType.LEFT)
                setTypeIfSafe(level, midPos, SafeChestType.RIGHT)
            }
            SafeChestType.SINGLE -> { }
        }
    }

    private fun getConnectDirection(facing: Direction): Direction = facing.clockWise

    private fun tryMergeWithNeighbors(level: Level, pos: BlockPos, facing: Direction) {
        val rightDir = getConnectDirection(facing)
        val leftDir = rightDir.opposite

        val leftPos = pos.relative(leftDir)
        val leftState = level.getBlockState(leftPos)
        val leftIsChest = leftState.block is SafeChestBlock && leftState.getValue(FACING) == facing

        val rightPos = pos.relative(rightDir)
        val rightState = level.getBlockState(rightPos)
        val rightIsChest = rightState.block is SafeChestBlock && rightState.getValue(FACING) == facing

        if (leftIsChest && rightIsChest) {
            val leftType = leftState.getValue(CHEST_TYPE)
            val rightType = rightState.getValue(CHEST_TYPE)
            if (leftType == SafeChestType.SINGLE && rightType == SafeChestType.SINGLE) {
                level.setBlock(leftPos, leftState.setValue(CHEST_TYPE, SafeChestType.TRIPLE_LEFT), 3)
                level.setBlock(pos, level.getBlockState(pos).setValue(CHEST_TYPE, SafeChestType.TRIPLE_MIDDLE), 3)
                level.setBlock(rightPos, rightState.setValue(CHEST_TYPE, SafeChestType.TRIPLE_RIGHT), 3)
                resizeChestEntity(level, leftPos, SafeChestType.TRIPLE_LEFT)
                resizeChestEntity(level, pos, SafeChestType.TRIPLE_MIDDLE)
                resizeChestEntity(level, rightPos, SafeChestType.TRIPLE_RIGHT)
                return
            }
        }

        if (leftIsChest) {
            val leftType = leftState.getValue(CHEST_TYPE)
            if (leftType == SafeChestType.SINGLE) {
                level.setBlock(leftPos, leftState.setValue(CHEST_TYPE, SafeChestType.LEFT), 3)
                level.setBlock(pos, level.getBlockState(pos).setValue(CHEST_TYPE, SafeChestType.RIGHT), 3)
                resizeChestEntity(level, leftPos, SafeChestType.LEFT)
                resizeChestEntity(level, pos, SafeChestType.RIGHT)
                return
            }
            if (leftType == SafeChestType.RIGHT) {
                val farLeftPos = leftPos.relative(leftDir)
                val farLeftState = level.getBlockState(farLeftPos)
                if (farLeftState.block is SafeChestBlock &&
                    farLeftState.getValue(FACING) == facing &&
                    farLeftState.getValue(CHEST_TYPE) == SafeChestType.LEFT
                ) {
                    level.setBlock(farLeftPos, farLeftState.setValue(CHEST_TYPE, SafeChestType.TRIPLE_LEFT), 3)
                    level.setBlock(leftPos, leftState.setValue(CHEST_TYPE, SafeChestType.TRIPLE_MIDDLE), 3)
                    level.setBlock(pos, level.getBlockState(pos).setValue(CHEST_TYPE, SafeChestType.TRIPLE_RIGHT), 3)
                    resizeChestEntity(level, farLeftPos, SafeChestType.TRIPLE_LEFT)
                    resizeChestEntity(level, leftPos, SafeChestType.TRIPLE_MIDDLE)
                    resizeChestEntity(level, pos, SafeChestType.TRIPLE_RIGHT)
                    return
                }
            }
        }

        if (rightIsChest) {
            val rightType = rightState.getValue(CHEST_TYPE)
            if (rightType == SafeChestType.SINGLE) {
                level.setBlock(pos, level.getBlockState(pos).setValue(CHEST_TYPE, SafeChestType.LEFT), 3)
                level.setBlock(rightPos, rightState.setValue(CHEST_TYPE, SafeChestType.RIGHT), 3)
                resizeChestEntity(level, pos, SafeChestType.LEFT)
                resizeChestEntity(level, rightPos, SafeChestType.RIGHT)
                return
            }
            if (rightType == SafeChestType.LEFT) {
                val farRightPos = rightPos.relative(rightDir)
                val farRightState = level.getBlockState(farRightPos)
                if (farRightState.block is SafeChestBlock &&
                    farRightState.getValue(FACING) == facing &&
                    farRightState.getValue(CHEST_TYPE) == SafeChestType.RIGHT
                ) {
                    level.setBlock(pos, level.getBlockState(pos).setValue(CHEST_TYPE, SafeChestType.TRIPLE_LEFT), 3)
                    level.setBlock(rightPos, rightState.setValue(CHEST_TYPE, SafeChestType.TRIPLE_MIDDLE), 3)
                    level.setBlock(farRightPos, farRightState.setValue(CHEST_TYPE, SafeChestType.TRIPLE_RIGHT), 3)
                    resizeChestEntity(level, pos, SafeChestType.TRIPLE_LEFT)
                    resizeChestEntity(level, rightPos, SafeChestType.TRIPLE_MIDDLE)
                    resizeChestEntity(level, farRightPos, SafeChestType.TRIPLE_RIGHT)
                    return
                }
            }
        }
    }

    private fun resizeChestEntity(level: Level, pos: BlockPos, type: SafeChestType) {
        val be = level.getBlockEntity(pos) as? SafeChestBlockEntity ?: return
        be.resizeForType(type)
    }

    private fun setSingleIfSafe(level: Level, pos: BlockPos) {
        setTypeIfSafe(level, pos, SafeChestType.SINGLE)
    }

    private fun setTypeIfSafe(level: Level, pos: BlockPos, type: SafeChestType) {
        val state = level.getBlockState(pos)
        if (state.block is SafeChestBlock) {
            level.setBlock(pos, state.setValue(CHEST_TYPE, type), 3)
            val be = level.getBlockEntity(pos) as? SafeChestBlockEntity
            be?.resizeForType(type)
        }
    }

    fun getControllerPos(state: BlockState, pos: BlockPos): BlockPos {
        val facing = state.getValue(FACING)
        val rightDir = getConnectDirection(facing)
        return when (state.getValue(CHEST_TYPE)) {
            SafeChestType.SINGLE -> pos
            SafeChestType.LEFT -> pos
            SafeChestType.RIGHT -> pos.relative(rightDir.opposite)
            SafeChestType.TRIPLE_LEFT -> pos
            SafeChestType.TRIPLE_MIDDLE -> pos.relative(rightDir.opposite)
            SafeChestType.TRIPLE_RIGHT -> pos.relative(rightDir.opposite, 2)
        }
    }
}
