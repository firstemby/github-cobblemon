package com.cobblemon.mod.common.pokemon.requirements

import com.cobblemon.mod.common.api.pokemon.requirement.EntityQueryRequirement
import net.minecraft.world.entity.LivingEntity

/**
 * An [EntityQueryRequirement] that evaluates whether a [LivingEntity] is at a certain block level.
 *
 * @property minY The minimum block level to satisfy this requirement. If null, defaults to the dimension's min height.
 * @property maxY The maximum block level to satisfy this requirement. If null, defaults to the dimension's max height.
 * @author Segfault Guy
 * @since December 16, 2025
 */
class DepthRequirement(val minY: Int?, val maxY: Int?) : EntityQueryRequirement {

    override fun check(queriedEntity: LivingEntity): Boolean {
        val minLevelY = queriedEntity.level().minBuildHeight
        val maxLevelY = queriedEntity.level().maxBuildHeight
        val test = minY?.coerceIn(minLevelY, maxLevelY) ?: minLevelY
        val test2 = maxY?.coerceIn(minLevelY, maxLevelY) ?: maxLevelY

        return queriedEntity.blockPosition().y in test..test2
    }

    companion object {
        const val ADAPTER_VARIANT = "depth"
    }
}