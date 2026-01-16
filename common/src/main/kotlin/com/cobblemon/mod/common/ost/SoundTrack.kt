package com.cobblemon.mod.common.ost

import com.cobblemon.mod.common.api.pokemon.requirement.EntityQueryRequirement
import net.minecraft.resources.ResourceLocation

/**
 * r
 *
 * @author Segfault Guy
 * @since December 16, 2025
 */
open class SoundTrack {

    val ambient: ResourceLocation
    val wildLocation: ResourceLocation
    val trainerLocation: ResourceLocation
    val priority: Int = 1
    val requirements: MutableSet<EntityQueryRequirement>
}