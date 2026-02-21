package com.qc.aeonis.dimension

import com.qc.aeonis.block.AeonisBlocks
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.BlockTags
import net.minecraft.util.BlockUtil
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.NetherPortalBlock
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.apache.commons.lang3.mutable.MutableInt
import java.util.Optional

class AncardPortalShape private constructor(
    val axis: Direction.Axis,
    val numPortalBlocks: Int,
    private val rightDir: Direction,
    val bottomLeft: BlockPos,
    val width: Int,
    val height: Int
) {
    companion object {
        private const val MIN_WIDTH = 2
        const val MAX_WIDTH = 21
        private const val MIN_HEIGHT = 3
        const val MAX_HEIGHT = 21
        private val FRAME: BlockBehaviour.StatePredicate =
            BlockBehaviour.StatePredicate { state, _, _ -> state.`is`(Blocks.REINFORCED_DEEPSLATE) }

        fun findEmptyPortalShape(level: LevelAccessor, pos: BlockPos, axis: Direction.Axis): Optional<AncardPortalShape> {
            return findPortalShape(level, pos, { shape -> shape.isValid() && shape.numPortalBlocks == 0 }, axis)
        }

        fun findPortalShape(level: LevelAccessor, pos: BlockPos, predicate: (AncardPortalShape) -> Boolean, axis: Direction.Axis): Optional<AncardPortalShape> {
            val first = Optional.of(findAnyShape(level, pos, axis)).filter(predicate)
            if (first.isPresent) return first
            val altAxis = if (axis == Direction.Axis.X) Direction.Axis.Z else Direction.Axis.X
            return Optional.of(findAnyShape(level, pos, altAxis)).filter(predicate)
        }

        fun findAnyShape(level: BlockGetter, pos: BlockPos, axis: Direction.Axis): AncardPortalShape {
            val right = if (axis == Direction.Axis.X) Direction.WEST else Direction.SOUTH
            val bottomLeft = calculateBottomLeft(level, right, pos) ?: return AncardPortalShape(axis, 0, right, pos, 0, 0)
            val width = calculateWidth(level, bottomLeft, right)
            if (width == 0) return AncardPortalShape(axis, 0, right, bottomLeft, 0, 0)
            val portalCount = MutableInt()
            val height = calculateHeight(level, bottomLeft, right, width, portalCount)
            return AncardPortalShape(axis, portalCount.value, right, bottomLeft, width, height)
        }

        private fun calculateBottomLeft(level: BlockGetter, right: Direction, pos: BlockPos): BlockPos? {
            var cursor = pos
            val minY = maxOf(level.minY, pos.y - MAX_HEIGHT)
            while (cursor.y > minY && isEmpty(level.getBlockState(cursor.below()))) {
                cursor = cursor.below()
            }
            val left = right.opposite
            val distance = getDistanceUntilEdgeAboveFrame(level, cursor, left) - 1
            return if (distance < 0) null else cursor.relative(left, distance)
        }

        private fun calculateWidth(level: BlockGetter, pos: BlockPos, right: Direction): Int {
            val distance = getDistanceUntilEdgeAboveFrame(level, pos, right)
            return if (distance in MIN_WIDTH..MAX_WIDTH) distance else 0
        }

        private fun getDistanceUntilEdgeAboveFrame(level: BlockGetter, pos: BlockPos, right: Direction): Int {
            val cursor = BlockPos.MutableBlockPos()
            for (i in 0..MAX_WIDTH) {
                cursor.set(pos).move(right, i)
                val state = level.getBlockState(cursor)
                if (!isEmpty(state)) {
                    if (FRAME.test(state, level, cursor)) {
                        return i
                    }
                    break
                }
                val below = level.getBlockState(cursor.move(Direction.DOWN))
                if (!FRAME.test(below, level, cursor)) {
                    break
                }
            }
            return 0
        }

        private fun calculateHeight(level: BlockGetter, pos: BlockPos, right: Direction, width: Int, portalCount: MutableInt): Int {
            val cursor = BlockPos.MutableBlockPos()
            val height = getDistanceUntilTop(level, pos, right, cursor, width, portalCount)
            return if (height in MIN_HEIGHT..MAX_HEIGHT && hasTopFrame(level, pos, right, cursor, width, height)) height else 0
        }

        private fun hasTopFrame(level: BlockGetter, pos: BlockPos, right: Direction, cursor: BlockPos.MutableBlockPos, width: Int, height: Int): Boolean {
            for (i in 0 until width) {
                val check = cursor.set(pos).move(Direction.UP, height).move(right, i)
                if (!FRAME.test(level.getBlockState(check), level, check)) {
                    return false
                }
            }
            return true
        }

        private fun getDistanceUntilTop(
            level: BlockGetter,
            pos: BlockPos,
            right: Direction,
            cursor: BlockPos.MutableBlockPos,
            width: Int,
            portalCount: MutableInt
        ): Int {
            for (height in 0..MAX_HEIGHT) {
                cursor.set(pos).move(Direction.UP, height).move(right, -1)
                if (!FRAME.test(level.getBlockState(cursor), level, cursor)) {
                    return height
                }
                cursor.set(pos).move(Direction.UP, height).move(right, width)
                if (!FRAME.test(level.getBlockState(cursor), level, cursor)) {
                    return height
                }
                for (i in 0 until width) {
                    cursor.set(pos).move(Direction.UP, height).move(right, i)
                    val state = level.getBlockState(cursor)
                    if (!isEmpty(state)) {
                        return height
                    }
                    if (state.`is`(AeonisBlocks.ANCARD_PORTAL)) {
                        portalCount.increment()
                    }
                }
            }
            return MAX_HEIGHT
        }

        private fun isEmpty(state: BlockState): Boolean {
            return state.isAir || state.`is`(BlockTags.FIRE) || state.`is`(AeonisBlocks.ANCARD_PORTAL)
        }

        fun getRelativePosition(foundRectangle: BlockUtil.FoundRectangle, axis: Direction.Axis, pos: Vec3, dimensions: EntityDimensions): Vec3 {
            val width = foundRectangle.axis1Size.toDouble() - dimensions.width()
            val height = foundRectangle.axis2Size.toDouble() - dimensions.height()
            val corner = foundRectangle.minCorner
            val xRel = if (width > 0.0) {
                val center = corner.get(axis) + dimensions.width() / 2.0
                Mth.clamp(Mth.inverseLerp(pos.get(axis) - center, 0.0, width), 0.0, 1.0)
            } else {
                0.5
            }
            val yRel = if (height > 0.0) {
                Mth.clamp(Mth.inverseLerp(pos.y - corner.y.toDouble(), 0.0, height), 0.0, 1.0)
            } else {
                0.0
            }
            val axis2 = if (axis == Direction.Axis.X) Direction.Axis.Z else Direction.Axis.X
            val zRel = pos.get(axis2) - (corner.get(axis2) + 0.5)
            return Vec3(xRel, yRel, zRel)
        }

        fun findCollisionFreePosition(pos: Vec3, level: net.minecraft.server.level.ServerLevel, entity: Entity, dimensions: EntityDimensions): Vec3 {
            if (dimensions.width() > 4.0F || dimensions.height() > 4.0F) return pos
            val halfHeight = dimensions.height() / 2.0
            val center = pos.add(0.0, halfHeight.toDouble(), 0.0)
            val aabb = AABB.ofSize(center, dimensions.width().toDouble(), 0.0, dimensions.width().toDouble())
                .expandTowards(0.0, 1.0, 0.0)
                .inflate(1.0E-6)
            val shape: VoxelShape = Shapes.create(aabb)
            val candidate = level.findFreePosition(
                entity,
                shape,
                center,
                dimensions.width().toDouble(),
                dimensions.height().toDouble(),
                dimensions.width().toDouble()
            )
            val adjusted = candidate.map { it.subtract(0.0, halfHeight.toDouble(), 0.0) }
            return adjusted.orElse(pos)
        }
    }

    fun isValid(): Boolean {
        return width in MIN_WIDTH..MAX_WIDTH && height in MIN_HEIGHT..MAX_HEIGHT
    }

    fun createPortalBlocks(level: LevelAccessor) {
        val state = AeonisBlocks.ANCARD_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, axis)
        BlockPos.betweenClosed(
            bottomLeft,
            bottomLeft.relative(Direction.UP, height - 1).relative(rightDir, width - 1)
        ).forEach { level.setBlock(it, state, 18) }
    }

    fun isComplete(): Boolean {
        return isValid() && numPortalBlocks == width * height
    }
}
