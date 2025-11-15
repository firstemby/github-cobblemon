/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.ai

import com.bedrockk.molang.runtime.value.DoubleValue
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
    val dampensVibrations = false
    val entityInteract = EntityBehaviour()
    val lightningHit = ThunderstruckBehaviour()
    val blockInteract = BlockBehavior()
    val combat = CombatBehaviour()
    val herd = HerdBehaviour()
    val characteristicRainbow = false
    val itemInteract = ItemBehavior()

    @Transient
    val struct = ObjectValue<PokemonBehaviour>(this).also {
        it.addFunction("resting") { resting.struct }
        it.addFunction("moving") { moving.struct }
        it.addFunction("idle") { idle.struct }
        it.addFunction("entity_interact") { entityInteract.struct }
        it.addFunction("block_interact") { blockInteract.struct }
        it.addFunction("lightning_hit") { lightningHit.struct }
        it.addFunction("characteristic_rainbow") { DoubleValue(characteristicRainbow) }
        it.addFunction("combat") { combat.struct }
        it.addFunction("herd") { herd.struct }
        it.addFunction("item_interact") { itemInteract.struct }
    }
}