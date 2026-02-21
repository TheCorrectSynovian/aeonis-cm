package com.qc.aeonis.client.block

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.core.Direction

class SafeChestRenderState : BlockEntityRenderState() {
    var facing: Direction = Direction.NORTH
    var drawerProgress: Float = 0f
    var prevDrawerProgress: Float = 0f
}
