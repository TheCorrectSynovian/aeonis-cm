package com.qc.aeonis.block

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.ScheduledTickAccess
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.entity.InsideBlockEffectApplier
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * A permanent dark-red fire that can only be extinguished by breaking
 * the supporting block below it. Right-clicking or any item interaction
 * will NOT put it out.
 */
class PermanentFlameBlock(properties: Properties) : Block(properties) {

    companion object {
        // Dark red dust color: RGB(139, 0, 0) = 0x8B0000
        private const val FLAME_COLOR_DARK = 0x8B0000
        // Brighter red accent: RGB(200, 30, 10) = 0xC81E0A
        private const val FLAME_COLOR_BRIGHT = 0xC81E0A
    }

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape = Shapes.empty()

    override fun getCollisionShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape = Shapes.empty()

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    // Fire survives as long as the block below it is solid
    override fun canSurvive(state: BlockState, level: LevelReader, pos: BlockPos): Boolean {
        val below = pos.below()
        val belowState = level.getBlockState(below)
        return belowState.isFaceSturdy(level, below, Direction.UP)
    }

    // Only break when the block BELOW is removed (direction == DOWN means neighbor below changed)
    override fun updateShape(
        state: BlockState,
        level: LevelReader,
        scheduledTickAccess: ScheduledTickAccess,
        pos: BlockPos,
        direction: Direction,
        neighborPos: BlockPos,
        neighborState: BlockState,
        random: RandomSource
    ): BlockState {
        // If the block below was changed and we can't survive anymore, break
        if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState()
        }
        // For all other neighbor changes, keep the fire
        return state
    }

    // Block right-click without item — fire cannot be interacted with
    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hit: BlockHitResult
    ): InteractionResult = InteractionResult.PASS

    // Block right-click with item — fire cannot be extinguished by items (water bucket, etc.)
    override fun useItemOn(
        stack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult
    ): InteractionResult = InteractionResult.TRY_WITH_EMPTY_HAND

    // Spawn dark-red fire particles
    override fun animateTick(state: BlockState, level: Level, pos: BlockPos, random: RandomSource) {
        // Dark red flame particles — main body
        for (i in 0..3) {
            val x = pos.x + 0.2 + random.nextDouble() * 0.6
            val y = pos.y + 0.1 + random.nextDouble() * 0.6
            val z = pos.z + 0.2 + random.nextDouble() * 0.6
            level.addParticle(
                DustParticleOptions(FLAME_COLOR_DARK, 1.5f),
                x, y, z,
                0.0, 0.03, 0.0
            )
        }

        // Brighter red accent particles — inner core
        for (i in 0..1) {
            val x = pos.x + 0.3 + random.nextDouble() * 0.4
            val y = pos.y + 0.15 + random.nextDouble() * 0.4
            val z = pos.z + 0.3 + random.nextDouble() * 0.4
            level.addParticle(
                DustParticleOptions(FLAME_COLOR_BRIGHT, 0.8f),
                x, y, z,
                0.0, 0.05, 0.0
            )
        }

        // Occasional lava-style drip particles for extra glow
        if (random.nextInt(4) == 0) {
            val x = pos.x + 0.5 + (random.nextDouble() - 0.5) * 0.4
            val y = pos.y + 0.3 + random.nextDouble() * 0.3
            val z = pos.z + 0.5 + (random.nextDouble() - 0.5) * 0.4
            level.addParticle(
                net.minecraft.core.particles.ParticleTypes.LAVA,
                x, y, z,
                0.0, 0.0, 0.0
            )
        }

        // Dark smoke
        if (random.nextInt(5) == 0) {
            val x = pos.x + 0.5 + (random.nextDouble() - 0.5) * 0.4
            val y = pos.y + 0.7
            val z = pos.z + 0.5 + (random.nextDouble() - 0.5) * 0.4
            level.addParticle(
                net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                x, y, z,
                0.0, 0.04, 0.0
            )
        }

        // Fire crackle sound
        if (random.nextInt(24) == 0) {
            level.playLocalSound(
                pos.x.toDouble() + 0.5, pos.y.toDouble() + 0.5, pos.z.toDouble() + 0.5,
                net.minecraft.sounds.SoundEvents.FIRE_AMBIENT,
                net.minecraft.sounds.SoundSource.BLOCKS,
                1.0f + random.nextFloat(), random.nextFloat() * 0.7f + 0.3f,
                false
            )
        }
    }

    // Entities that walk through take fire damage
    override fun entityInside(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        entity: net.minecraft.world.entity.Entity,
        insideBlockEffectApplier: InsideBlockEffectApplier,
        bl: Boolean
    ) {
        if (!entity.fireImmune()) {
            entity.igniteForSeconds(4.0f)
            entity.hurt(level.damageSources().inFire(), 2.0f)
        }
    }
}
