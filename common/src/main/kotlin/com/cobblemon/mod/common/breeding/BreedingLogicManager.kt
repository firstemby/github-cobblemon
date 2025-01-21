/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.breeding

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.abilities.Ability
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.breeding.BreedingEligibilityEvent
import com.cobblemon.mod.common.api.events.breeding.BreedingResultEvent
import com.cobblemon.mod.common.api.moves.MoveSet
import com.cobblemon.mod.common.api.pokemon.breeding.BreedingLogic
import com.cobblemon.mod.common.api.pokemon.breeding.BreedingResult
import com.cobblemon.mod.common.pokeball.PokeBall
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.IVs
import com.cobblemon.mod.common.pokemon.Nature
import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * Contains all breeding logics to be run through when doing the breeding logic
 * Basically if a sidemod wants to override some aspect of breeding logic, they can implement the [BreedingLogic] interface
 * then add it here
 */
object BreedingLogicManager : BreedingLogic {
    override fun breed(mother: Pokemon, father: Pokemon): BreedingResult {
        // todo this was not working
        return super.breed(mother, father)
        /*val baseBreedingResult = super.breed(mother, father)
        var finalBreedingResult = baseBreedingResult
        CobblemonEvents.BREEDING_RESULT.post(BreedingResultEvent(baseBreedingResult, mother, father)) {
            finalBreedingResult = it.breedingResult
        }
        return finalBreedingResult*/
    }

    // todo this canBreed method is NOT working lmao
    override fun canBreed(mother: Pokemon, father: Pokemon): Boolean {
        // todo this was not working
        return super.canBreed(mother, father)
        /*val initBreedBool = super.canBreed(mother, father)
        var finalBreedingResult = initBreedBool
        CobblemonEvents.BREEDING_ELIGIBLE.post(BreedingEligibilityEvent(initBreedBool, mother, father) ) {
            finalBreedingResult = it.canBreed
        }
        return super.canBreed(mother, father)*/
    }
}