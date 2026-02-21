package com.qc.aeonis.dimension

import com.qc.aeonis.block.AeonisBlocks
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.BlockUtil
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.entity.Relative
import net.minecraft.world.level.block.NetherPortalBlock
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.border.WorldBorder
import net.minecraft.world.level.portal.TeleportTransition
import net.minecraft.world.phys.Vec3

object AncardTeleporter {
    fun getTeleportTransition(
        targetLevel: ServerLevel,
        entity: Entity,
        originPos: BlockPos,
        targetPos: BlockPos,
        worldBorder: WorldBorder
    ): TeleportTransition? {
        val forcer = AncardPortalForcer(targetLevel)
        val found = forcer.findClosestPortalPosition(targetPos, 16, worldBorder)
        val sourceState = entity.level().getBlockState(originPos)
        val sourceAxis = sourceState.getOptionalValue(NetherPortalBlock.AXIS).orElse(Direction.Axis.X)
        val sourceRelative = if (sourceState.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
            val sourceRect = BlockUtil.getLargestRectangleAround(
                originPos,
                sourceAxis,
                AncardPortalShape.MAX_WIDTH,
                Direction.Axis.Y,
                AncardPortalShape.MAX_HEIGHT
            ) { pos -> entity.level().getBlockState(pos) == sourceState }
            entity.getRelativePortalPosition(sourceAxis, sourceRect)
        } else {
            Vec3(0.5, 0.0, 0.0)
        }

        val rectangle = if (found.isPresent) {
            val portalPos = found.get()
            val blockState = targetLevel.getBlockState(portalPos)
            BlockUtil.getLargestRectangleAround(
                portalPos,
                blockState.getValue(BlockStateProperties.HORIZONTAL_AXIS),
                AncardPortalShape.MAX_WIDTH,
                Direction.Axis.Y,
                AncardPortalShape.MAX_HEIGHT
            ) { pos -> targetLevel.getBlockState(pos) == blockState }
        } else {
            val created = forcer.createPortal(targetPos, sourceAxis)
            if (created.isEmpty) return null
            created.get()
        }

        val post = TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET)
        return createTransition(targetLevel, rectangle, sourceAxis, sourceRelative, entity, post)
    }

    private fun createTransition(
        targetLevel: ServerLevel,
        rectangle: BlockUtil.FoundRectangle,
        sourceAxis: Direction.Axis,
        sourceRelative: Vec3,
        entity: Entity,
        post: TeleportTransition.PostTeleportTransition
    ): TeleportTransition? {
        val blockPos = rectangle.minCorner
        val blockState = targetLevel.getBlockState(blockPos)
        if (!blockState.`is`(AeonisBlocks.ANCARD_PORTAL)) return null
        val targetAxis = blockState.getOptionalValue(BlockStateProperties.HORIZONTAL_AXIS).orElse(Direction.Axis.X)
        val width = rectangle.axis1Size.toDouble()
        val height = rectangle.axis2Size.toDouble()
        val entityDimensions: EntityDimensions = entity.getDimensions(entity.pose)
        val rotation = if (sourceAxis == targetAxis) 0.0F else 90.0F
        val xOffset = entityDimensions.width() / 2.0 + (width - entityDimensions.width()) * sourceRelative.x
        val yOffset = (height - entityDimensions.height()) * sourceRelative.y
        val zOffset = 0.5 + sourceRelative.z
        val isXAxis = targetAxis == Direction.Axis.X
        val targetVec = Vec3(
            blockPos.x + if (isXAxis) xOffset else zOffset,
            blockPos.y + yOffset,
            blockPos.z + if (isXAxis) zOffset else xOffset
        )
        val safePos = AncardPortalShape.findCollisionFreePosition(targetVec, targetLevel, entity, entityDimensions)
        return TeleportTransition(
            targetLevel,
            safePos,
            Vec3.ZERO,
            rotation,
            0.0F,
            Relative.union(Relative.DELTA, Relative.ROTATION),
            post
        )
    }
}
