/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.movement

import com.bedrockk.molang.runtime.struct.QueryStruct
import com.bedrockk.molang.runtime.value.DoubleValue
import net.minecraft.world.entity.LivingEntity

/**
 * Creates the q.entity.movement struct: the entity's movement capabilities and preferences, the same for Pokémon and
 * NPCs. Values are read when queried, so they follow form changes and config edits.
 *
 * Unlike q.entity.behaviour.moving, which is the species data, this is what the movement system actually uses.
 * Lava has no swimming entry because every body can move through it; fire_immune decides whether it burns.
 *
 * @author Hiroku
 * @since October 1st, 2026
 */
fun createMovementStruct(entity: LivingEntity): QueryStruct {
    val capabilities = { MovementCapabilities.of(entity) }
    val preferences = { MediumPreferences.of(entity) }
    return QueryStruct(hashMapOf())
        .addFunction("can_walk") { DoubleValue(capabilities().canWalk) }
        .addFunction("can_fly") { DoubleValue(capabilities().canFly) }
        .addFunction("can_swim_in_water") { DoubleValue(capabilities().canSwimInWater) }
        .addFunction("can_breathe_underwater") { DoubleValue(capabilities().canBreatheUnderwater) }
        .addFunction("can_breathe_under_lava") { DoubleValue(capabilities().canBreatheUnderLava) }
        .addFunction("can_walk_on_water") { DoubleValue(capabilities().canWalkOnWater) }
        .addFunction("can_walk_on_lava") { DoubleValue(capabilities().canWalkOnLava) }
        .addFunction("fire_immune") { DoubleValue(capabilities().fireImmune) }
        .addFunction("avoids_water") { DoubleValue(preferences().avoidsWater) }
        .addFunction("avoids_land") { DoubleValue(preferences().avoidsLand) }
        .addFunction("prefers_lava") { DoubleValue(preferences().prefersLava) }
        .addFunction("is_seafloor_walker") {
            val caps = capabilities()
            DoubleValue(caps.canWalk && !caps.canSwimInWater && caps.canBreatheUnderwater)
        }
}
