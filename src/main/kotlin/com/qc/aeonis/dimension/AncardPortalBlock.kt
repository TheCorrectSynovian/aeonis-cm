package com.qc.aeonis.dimension

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.ScheduledTickAccess
import net.minecraft.world.level.block.NetherPortalBlock
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.border.WorldBorder
import net.minecraft.world.level.dimension.DimensionType
import net.minecraft.world.level.portal.TeleportTransition

class AncardPortalBlock(properties: BlockBehaviour.Properties) : NetherPortalBlock(properties) {
    override fun getPortalTransitionTime(serverLevel: ServerLevel, entity: Entity): Int {
        return if (entity is Player) {
            super.getPortalTransitionTime(serverLevel, entity)
        } else {
            0
        }
    }

    override fun animateTick(state: BlockState, level: Level, pos: BlockPos, random: RandomSource) {
        // Eerie ambient sound
        if (random.nextInt(80) == 0) {
            level.playLocalSound(
                pos.x + 0.5,
                pos.y + 0.5,
                pos.z + 0.5,
                SoundEvents.WARDEN_HEARTBEAT,
                SoundSource.BLOCKS,
                0.4F,
                0.8F + random.nextFloat() * 0.2F,
                false
            )
        }

        // Brilliant glowing core particles
        for (i in 0 until 6) {
            val x = pos.x + random.nextDouble()
            val y = pos.y + random.nextDouble()
            val z = pos.z + random.nextDouble()
            val dx = (random.nextDouble() - 0.5) * 0.1
            val dy = (random.nextDouble() - 0.5) * 0.15
            val dz = (random.nextDouble() - 0.5) * 0.1

            // Bright lavender-white glow
            level.addParticle(DustParticleOptions(0xE0C0FF, 1.8f), x, y, z, dx, dy, dz)

            // Soul fire flames rising
            if (random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, dx * 0.3, 0.02, dz * 0.3)
            }

            // End rod sparkles
            if (random.nextInt(5) == 0) {
                level.addParticle(ParticleTypes.END_ROD, x, y, z, dx * 0.1, dy * 0.2, dz * 0.1)
            }
        }

        // Enchantment glyphs floating outward
        if (random.nextInt(2) == 0) {
            val x = pos.x + 0.5 + (random.nextDouble() - 0.5) * 3.0
            val y = pos.y + random.nextDouble() * 2.0
            val z = pos.z + 0.5 + (random.nextDouble() - 0.5) * 3.0
            level.addParticle(
                ParticleTypes.ENCHANT,
                x, y, z,
                (random.nextDouble() - 0.5) * 0.5,
                random.nextDouble() * 0.3,
                (random.nextDouble() - 0.5) * 0.5
            )
        }

        // Ambient purple glow aura (wider radius)
        if (random.nextInt(3) == 0) {
            val rx = pos.x + 0.5 + (random.nextDouble() - 0.5) * 4.0
            val ry = pos.y + random.nextDouble() * 2.5
            val rz = pos.z + 0.5 + (random.nextDouble() - 0.5) * 4.0
            level.addParticle(DustParticleOptions(0xAA80DD, 1.0f), rx, ry, rz, 0.0, 0.02, 0.0)
        }
    }

    override fun randomTick(state: BlockState, level: ServerLevel, pos: BlockPos, random: RandomSource) {
        // No entity spawning from Ancard portals.
    }

    override fun getPortalDestination(serverLevel: ServerLevel, entity: Entity, pos: BlockPos): TeleportTransition? {
        val targetKey: ResourceKey<Level> = if (serverLevel.dimension() == AeonisDimensions.ANCARD) {
            Level.OVERWORLD
        } else {
            AeonisDimensions.ANCARD
        }
        val targetLevel = serverLevel.server.getLevel(targetKey) ?: return null
        val worldBorder: WorldBorder = targetLevel.worldBorder
        val scale = DimensionType.getTeleportationScale(serverLevel.dimensionType(), targetLevel.dimensionType())
        val targetPos = worldBorder.clampToBounds(entity.x * scale, entity.y, entity.z * scale)
        return AncardTeleporter.getTeleportTransition(targetLevel, entity, pos, targetPos, worldBorder)
    }

    override fun updateShape(
        state: BlockState,
        levelReader: LevelReader,
        tickAccess: ScheduledTickAccess,
        pos: BlockPos,
        direction: Direction,
        neighborPos: BlockPos,
        neighborState: BlockState,
        random: RandomSource
    ): BlockState {
        val axis = direction.axis
        val portalAxis = state.getValue(AXIS)
        val horizontalBreak = portalAxis != axis && axis.isHorizontal
        return if (!horizontalBreak && !neighborState.`is`(this) && !AncardPortalShape.findAnyShape(levelReader, pos, portalAxis).isComplete()) {
            net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
        } else {
            super.updateShape(state, levelReader, tickAccess, pos, direction, neighborPos, neighborState, random)
        }
    }

    override fun getCloneItemStack(levelReader: LevelReader, pos: BlockPos, state: BlockState, includeData: Boolean): ItemStack {
        return ItemStack.EMPTY
    }
}
