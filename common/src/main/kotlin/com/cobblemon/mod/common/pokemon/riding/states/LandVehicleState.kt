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

data class LandVehicleState(val entity: PokemonEntity) : RidingState {
    var currSpeed: Double = 0.0
    var stamina: Float = 1.0f
    var currMouseXForce: Double = 0.0
    var currMouseYForce: Double = 0.0
    //I am thinking this will either be a ranging from -1.0 to 1.0
    //When drift is first activated while a turn is initiated it will go to (the sign of the turn)*0.5.
    //Then you are able to change it between +-1.0 and 0.25 which will determine how much forced rotation
    //The drift gives you. The second component is the offset of the velocity vector. Might simply be able
    //to add an equal amount of x velocity as the current z velocity? Or maybe just a transformation to turn
    //it to the right?? This will be the hardest part actually... Because if you add a similar one then the
    //magnitude willbe increased? Maybe just half both and then that effectively slows it? idk.
    var currDrift: Double = 0.0
    //Determines how much boost is left.
    var driftBoost: Double = 0.0
    var turnSmoother: SmoothDouble = SmoothDouble()

}