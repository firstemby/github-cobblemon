/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.triggers

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.transformation.trigger.ContextTrigger
import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * Represents a [ContextTrigger] with [Pokemon] context.
 * This is triggered by trading.
 * The context is the received [Pokemon] from the trade.
 *
 * @property requiredContext The [PokemonProperties] representation of the expected received [Pokemon] from the trade.
 * @author Licious
 * @since March 20th, 2022
 */
open class TradeTrigger(
    override val requiredContext: PokemonProperties = PokemonProperties()
) : ContextTrigger<Pokemon, PokemonProperties> {

    override fun testContext(pokemon: Pokemon, context: Pokemon) = this.requiredContext.matches(context)

    companion object {
        const val ADAPTER_VARIANT = "trade"
    }
}