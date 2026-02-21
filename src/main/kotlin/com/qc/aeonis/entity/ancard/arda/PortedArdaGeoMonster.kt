package com.qc.aeonis.entity.ancard.arda

import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.level.Level
import software.bernie.geckolib.animatable.GeoEntity
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.util.GeckoLibUtil

abstract class PortedArdaGeoMonster(
    type: EntityType<out Monster>,
    level: Level,
    val modelId: String,
    val idleAnim: String,
    val walkAnim: String,
    val attackAnim: String,
) : Monster(type, level), GeoEntity {
    private val cache: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)
    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = cache
}

