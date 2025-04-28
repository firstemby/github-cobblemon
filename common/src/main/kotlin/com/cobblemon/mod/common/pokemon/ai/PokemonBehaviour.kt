/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.ai

import com.cobblemon.mod.common.api.molang.ObjectValue

/**
 * Collection of all AI properties definable at the species level of a Pokémon.
 *
 * @author Hiroku
 * @since July 15th, 2022
 */
open class PokemonBehaviour {
    val resting = RestBehaviour()
    var moving = MoveBehaviour()
    val idle = IdleBehaviour()
    val fireImmune = false
    val entityInteract = EntityBehaviour()
    val combat = CombatBehaviour()

    @Transient
    val struct = ObjectValue<PokemonBehaviour>(this).also {
        it.addFunction("resting") { resting.struct }
        it.addFunction("moving") { moving.struct }
        it.addFunction("idle") { idle.struct }
        it.addFunction("entity_interact") { entityInteract.struct }
        it.addFunction("combat") { combat.struct }
    }
}