/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.breeding.rule.layer

import com.cobblemon.mod.common.api.breeding.rule.BreedingRule
import com.cobblemon.mod.common.pokemon.breeding.rules.shiny.EmptyShinyBreedingRule
import com.cobblemon.mod.common.pokemon.breeding.rules.shiny.ExpressionShinyBreedingRule
import com.cobblemon.mod.common.util.cobblemonResource

/**
 * A breeding rule which modifies the chance of an offspring being shiny.
 *
 * @author Hiroku
 * @since April 3rd, 2026
 */
interface ShinyBreedingRule : BreedingRule {
    companion object {
        val types = mutableMapOf(
            cobblemonResource("expression") to ExpressionShinyBreedingRule::class.java,
            cobblemonResource("empty") to EmptyShinyBreedingRule::class.java
        )
    }

    fun getShinyChance(input: PokemonBreedingRule.Input, shinyRate: Float): Float
}