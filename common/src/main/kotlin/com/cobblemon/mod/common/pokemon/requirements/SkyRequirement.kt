package com.cobblemon.mod.common.pokemon.requirements

import com.cobblemon.mod.common.api.pokemon.requirement.EntityQueryRequirement
import net.minecraft.world.entity.LivingEntity

/**
 * An [EntityQueryRequirement] that evaluates whether a [LivingEntity] is exposed to a dimension's sky.
 *
 * @param invert If true, this requirement evaluates whether the entity CANNOT see a dimension's sky.
 * @author Segfault Guy
 * @since December 16, 2025
 */
class SkyRequirement(val invert: Boolean = false) : EntityQueryRequirement {

    override fun check(queriedEntity: LivingEntity) = queriedEntity.level().canSeeSky(queriedEntity.blockPosition()).xor(invert)

    companion object {
        const val ADAPTER_VARIANT = "sky"
    }
}