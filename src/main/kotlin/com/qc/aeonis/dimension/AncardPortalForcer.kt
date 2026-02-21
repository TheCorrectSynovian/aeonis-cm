package com.qc.aeonis.dimension

import com.qc.aeonis.block.AeonisBlocks
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.BlockUtil
import net.minecraft.util.Mth
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.NetherPortalBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.border.WorldBorder
import net.minecraft.world.level.levelgen.Heightmap
import java.util.Optional

class AncardPortalForcer(private val level: ServerLevel) {
    fun findClosestPortalPosition(origin: BlockPos, radius: Int, worldBorder: WorldBorder): Optional<BlockPos> {
        var bestPos: BlockPos? = null
        var bestDistance = Double.MAX_VALUE
        val minY = maxOf(level.minY, origin.y - 48)
        val maxY = minOf(level.maxY - 1, origin.y + 48)
        for (pos in BlockPos.spiralAround(origin, radius, Direction.EAST, Direction.SOUTH)) {
            if (!worldBorder.isWithinBounds(pos)) continue
            for (y in minY..maxY) {
                val check = BlockPos(pos.x, y, pos.z)
                if (level.getBlockState(check).`is`(AeonisBlocks.ANCARD_PORTAL)) {
                    val dist = check.distSqr(origin)
                    if (dist < bestDistance) {
                        bestDistance = dist
                        bestPos = check
                    }
                }
            }
        }
        return Optional.ofNullable(bestPos)
    }

    fun createPortal(origin: BlockPos, axis: Direction.Axis): Optional<BlockUtil.FoundRectangle> {
        val direction = Direction.get(Direction.AxisDirection.POSITIVE, axis)
        val worldBorder = level.worldBorder
        val minY = level.minY + 2
        val maxY = minOf(level.maxY - 5, level.getLogicalHeight() + level.minY - 5)
        var bestPos: BlockPos? = null
        var bestDistance = Double.MAX_VALUE

        for (pos in BlockPos.spiralAround(origin, 16, Direction.EAST, Direction.SOUTH)) {
            if (!worldBorder.isWithinBounds(pos)) continue
            val surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.x, pos.z)
            var y = Mth.clamp(surfaceY, minY, maxY)
            while (y >= minY) {
                val base = BlockPos(pos.x, y, pos.z)
                if (canHostPortal(base, direction)) {
                    val dist = base.distSqr(origin)
                    if (dist < bestDistance) {
                        bestDistance = dist
                        bestPos = base
                    }
                    break
                }
                y--
            }
        }

        val anchor = bestPos ?: BlockPos(origin.x, Mth.clamp(origin.y, minY, maxY), origin.z)
        buildPortal(anchor, direction)
        return Optional.of(BlockUtil.FoundRectangle(anchor, 2, 3))
    }

    private fun canHostPortal(base: BlockPos, direction: Direction): Boolean {
        val mutable = BlockPos.MutableBlockPos()
        for (x in -1..2) {
            for (y in -1..3) {
                val frame = x == -1 || x == 2 || y == -1 || y == 3
                mutable.set(base).move(direction, x).move(Direction.UP, y)
                val state = level.getBlockState(mutable)
                if (frame) {
                    if (!state.canBeReplaced()) return false
                } else {
                    if (!state.canBeReplaced() || !state.fluidState.isEmpty) return false
                }
            }
        }
        return true
    }

    private fun buildPortal(base: BlockPos, direction: Direction) {
        val mutable = BlockPos.MutableBlockPos()
        for (x in -1..2) {
            for (y in -1..3) {
                val isFrame = x == -1 || x == 2 || y == -1 || y == 3
                val block: BlockState = if (isFrame) {
                    Blocks.REINFORCED_DEEPSLATE.defaultBlockState()
                } else {
                    AeonisBlocks.ANCARD_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, direction.axis)
                }
                mutable.set(base).move(direction, x).move(Direction.UP, y)
                level.setBlock(mutable, block, 18)
            }
        }
    }
}
