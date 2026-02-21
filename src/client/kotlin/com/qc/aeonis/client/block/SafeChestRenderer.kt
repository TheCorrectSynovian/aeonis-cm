package com.qc.aeonis.client.block

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.qc.aeonis.block.SafeChestBlock
import com.qc.aeonis.block.entity.SafeChestBlockEntity
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.Direction
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.Vec3

/**
 * Renders a drawer sliding out from the front of the safe chest when opened.
 * The drawer extends forward based on the open progress of the block entity.
 */
class SafeChestRenderer(ctx: BlockEntityRendererProvider.Context) :
    BlockEntityRenderer<SafeChestBlockEntity, SafeChestRenderState> {

    companion object {
        private val DRAWER_TEXTURE = Identifier.fromNamespaceAndPath("aeonis", "textures/block/safe_chest_side.png")
        private val DRAWER_FRONT_TEXTURE = Identifier.fromNamespaceAndPath("aeonis", "textures/block/safe_chest_front.png")
        private const val DRAWER_HEIGHT = 0.375f   // 6 pixels / 16
        private const val DRAWER_DEPTH = 0.75f     // 12 pixels / 16
        private const val DRAWER_INSET = 0.0625f   // 1 pixel / 16
        private const val MAX_EXTENSION = 0.5f     // How far the drawer slides out
    }

    override fun createRenderState(): SafeChestRenderState {
        return SafeChestRenderState()
    }

    override fun extractRenderState(
        entity: SafeChestBlockEntity,
        state: SafeChestRenderState,
        partialTick: Float,
        cameraPos: Vec3,
        crumblingOverlay: ModelFeatureRenderer.CrumblingOverlay?
    ) {
        super.extractRenderState(entity, state, partialTick, cameraPos, crumblingOverlay)
        val blockState = entity.blockState
        if (blockState.hasProperty(SafeChestBlock.FACING)) {
            state.facing = blockState.getValue(SafeChestBlock.FACING)
        }
        state.drawerProgress = entity.drawerProgress
        state.prevDrawerProgress = entity.prevDrawerProgress
    }

    override fun submit(
        state: SafeChestRenderState,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        cameraRenderState: CameraRenderState
    ) {
        // Interpolate drawer progress
        val progress = state.prevDrawerProgress + (state.drawerProgress - state.prevDrawerProgress) * 0.5f
        if (progress <= 0.001f) return

        val extension = progress * MAX_EXTENSION
        val facing = state.facing

        poseStack.pushPose()

        // Rotate based on facing direction
        poseStack.translate(0.5, 0.0, 0.5)
        val yRot = when (facing) {
            Direction.NORTH -> 0f
            Direction.SOUTH -> 180f
            Direction.WEST -> 90f
            Direction.EAST -> -90f
            else -> 0f
        }
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yRot))
        poseStack.translate(-0.5, 0.0, -0.5)

        // Drawer box dimensions
        val x0 = DRAWER_INSET
        val x1 = 1.0f - DRAWER_INSET
        val y0 = DRAWER_INSET
        val y1 = y0 + DRAWER_HEIGHT
        val z0 = -extension
        val z1 = DRAWER_DEPTH

        // Draw side faces with side texture
        submitNodeCollector.submitCustomGeometry(
            poseStack,
            RenderTypes.entitySolid(DRAWER_TEXTURE)
        ) { pose, consumer ->
            val light = state.lightCoords

            // Top face
            addVertex(consumer, pose, x0, y1, z0, 0f, 0f, 0f, 1f, 0f, light)
            addVertex(consumer, pose, x1, y1, z0, 1f, 0f, 0f, 1f, 0f, light)
            addVertex(consumer, pose, x1, y1, z1, 1f, 1f, 0f, 1f, 0f, light)
            addVertex(consumer, pose, x0, y1, z1, 0f, 1f, 0f, 1f, 0f, light)

            // Bottom face
            addVertex(consumer, pose, x0, y0, z1, 0f, 0f, 0f, -1f, 0f, light)
            addVertex(consumer, pose, x1, y0, z1, 1f, 0f, 0f, -1f, 0f, light)
            addVertex(consumer, pose, x1, y0, z0, 1f, 1f, 0f, -1f, 0f, light)
            addVertex(consumer, pose, x0, y0, z0, 0f, 1f, 0f, -1f, 0f, light)

            // Left side
            addVertex(consumer, pose, x0, y0, z1, 0f, 1f, -1f, 0f, 0f, light)
            addVertex(consumer, pose, x0, y0, z0, 1f, 1f, -1f, 0f, 0f, light)
            addVertex(consumer, pose, x0, y1, z0, 1f, 0f, -1f, 0f, 0f, light)
            addVertex(consumer, pose, x0, y1, z1, 0f, 0f, -1f, 0f, 0f, light)

            // Right side
            addVertex(consumer, pose, x1, y0, z0, 0f, 1f, 1f, 0f, 0f, light)
            addVertex(consumer, pose, x1, y0, z1, 1f, 1f, 1f, 0f, 0f, light)
            addVertex(consumer, pose, x1, y1, z1, 1f, 0f, 1f, 0f, 0f, light)
            addVertex(consumer, pose, x1, y1, z0, 0f, 0f, 1f, 0f, 0f, light)
        }

        // Draw front face with front texture
        submitNodeCollector.submitCustomGeometry(
            poseStack,
            RenderTypes.entitySolid(DRAWER_FRONT_TEXTURE)
        ) { pose, consumer ->
            val light = state.lightCoords

            // Front face
            addVertex(consumer, pose, x0, y0, z0, 0f, 1f, 0f, 0f, -1f, light)
            addVertex(consumer, pose, x1, y0, z0, 1f, 1f, 0f, 0f, -1f, light)
            addVertex(consumer, pose, x1, y1, z0, 1f, 0f, 0f, 0f, -1f, light)
            addVertex(consumer, pose, x0, y1, z0, 0f, 0f, 0f, 0f, -1f, light)
        }

        poseStack.popPose()
    }

    private fun addVertex(
        consumer: VertexConsumer,
        pose: PoseStack.Pose,
        x: Float, y: Float, z: Float,
        u: Float, v: Float,
        nx: Float, ny: Float, nz: Float,
        light: Int
    ) {
        consumer.addVertex(pose, x, y, z)
            .setColor(1f, 1f, 1f, 1f)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, nx, ny, nz)
    }
}
