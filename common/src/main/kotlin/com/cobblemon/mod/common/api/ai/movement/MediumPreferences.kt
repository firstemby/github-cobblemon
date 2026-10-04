/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.movement

import com.cobblemon.mod.common.entity.MoLangScriptingEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.world.entity.LivingEntity

/**
 * What an entity would rather avoid, as opposed to what it can't do at all ([MovementCapabilities]). These turn into
 * a discomfort for each occupancy, which [MovementIntent] tolerates to differing degrees.
 *
 * @author Hiroku
 * @since October 1st, 2026
 */
data class MediumPreferences(
    val avoidsWater: Boolean = false,
    val avoidsLand: Boolean = false,
    /** Whether the entity is comfortable in lava, assuming it's fire immune. The species' canSwimInLava. */
    val prefersLava: Boolean = false,
) {
    /**
     * How uncomfortable the entity is entering the occupancy, from 0 (natural) to 1 (only when desperate), or null if
     * it will never do so voluntarily. Hazards like burning or holding its breath are covered here too, since a
     * [MovementIntent] never tolerates them. Escaping a fluid uses its own rules.
     */
    fun discomfort(occupancy: Occupancy, fluid: FluidKind, capabilities: MovementCapabilities): Float? {
        if (fluid == FluidKind.LAVA) {
            return when {
                capabilities.isHarmedBy(FluidKind.LAVA) -> null
                prefersLava -> 0F
                else -> LAVA_DISCOMFORT
            }
        }
        val breathes = capabilities.canBreatheIn(fluid)
        return when (occupancy) {
            Occupancy.WALK -> if (avoidsLand) 0.8F else 0F
            Occupancy.FLY -> 0F
            Occupancy.WADE -> if (avoidsWater) 0.7F else 0.3F
            Occupancy.SEABED -> if (breathes) 0F else null
            // Non-breathers swimming is a bit more than idle wandering will do, which matches the old wander filter.
            Occupancy.SWIM -> if (breathes) 0F else if (avoidsWater) 0.8F else 0.4F
            Occupancy.SWIM_SUBMERGED -> if (breathes) 0F else if (avoidsWater) 0.9F else 0.5F
            Occupancy.SINK, Occupancy.FALL -> null
        }
    }

    companion object {
        const val LAVA_DISCOMFORT = 0.5F

        // Config variable names, shared with NPCs which have no species behaviour to read these from.
        const val AVOIDS_WATER = "avoids_water"
        const val AVOIDS_LAND = "avoids_land"
        const val PREFERS_LAVA = "prefers_lava"

        fun of(entity: LivingEntity): MediumPreferences {
            if (entity is PokemonEntity) {
                // The exposed form, like MovementCapabilities, so transformations and illusions agree.
                val moving = entity.exposedForm.behaviour.moving
                return MediumPreferences(
                    avoidsWater = moving.swim.avoidsWater,
                    avoidsLand = moving.walk.avoidsLand,
                    prefersLava = moving.swim.canSwimInLava
                )
            } else if (entity is MoLangScriptingEntity) {
                return MediumPreferences(
                    avoidsWater = entity.configFlag(AVOIDS_WATER, false),
                    avoidsLand = entity.configFlag(AVOIDS_LAND, false),
                    prefersLava = entity.configFlag(PREFERS_LAVA, false)
                )
            }
            return MediumPreferences()
        }
    }
}

/** Reads a boolean config variable, falling back if it isn't declared. */
fun MoLangScriptingEntity.configFlag(name: String, default: Boolean): Boolean {
    // Vanilla may ask things like fireImmune() while the entity is still being constructed, before config exists.
    @Suppress("SENSELESS_COMPARISON")
    if (config == null) return default
    val value = config.map[name] ?: return default
    return value.asDouble() != 0.0
}

/**
 * Why an entity is moving, which decides how much discomfort it will put up with along the way.
 *
 * @author Hiroku
 * @since October 1st, 2026
 */
enum class MovementIntent(val tolerance: Float) {
    /** Wandering and other things it's doing out of boredom. */
    IDLE(0.3F),
    /** Getting somewhere it would be more comfortable, like next to its owner. */
    COMFORT(0.6F),
    /** Chasing, fleeing, defending. Anything it can survive is on the table. */
    URGENT(1F)
}
