/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.riding.states

import com.cobblemon.mod.common.api.riding.RidingState
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.util.SmoothDouble
import net.minecraft.world.phys.Vec3

data class VehicleLandState(val entity: PokemonEntity) : RidingState {
    override var rideVel: Vec3 = Vec3.ZERO
    override var stamina: Float = 1.0f
    var currDrift: Double = 0.0
    var driftBoost: Double = 0.0
    var turnSmoother: SmoothDouble = SmoothDouble()
}