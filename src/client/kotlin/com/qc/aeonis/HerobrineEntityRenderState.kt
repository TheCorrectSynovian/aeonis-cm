package com.qc.aeonis

import net.minecraft.client.renderer.entity.state.HumanoidRenderState
import software.bernie.geckolib.constant.dataticket.DataTicket
import software.bernie.geckolib.renderer.base.GeoRenderState
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap
import java.util.Map

class HerobrineEntityRenderState : HumanoidRenderState(), GeoRenderState {
    private val geckolibData: MutableMap<DataTicket<*>, Any> = Reference2ObjectOpenHashMap()

    override fun getDataMap(): MutableMap<DataTicket<*>, Any> = geckolibData
}
