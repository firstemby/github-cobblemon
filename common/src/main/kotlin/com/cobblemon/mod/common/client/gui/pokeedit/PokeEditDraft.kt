/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokeedit

import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.pokemon.EVs
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.IVs
import com.cobblemon.mod.common.pokemon.Species

data class PokeEditDraft(
    var species: Species,
    var form: FormData = species.standardForm,
    var shiny: Boolean = false,
    var gender: Gender = defaultGenderFor(form),
    var level: Int = 1,
    var abilityIndex: Int = 0,
    var ivs: IVs = IVs(),
    var evs: EVs = EVs(),
    var cosmeticItem: String? = null,
    var cosmeticAspects: Set<String> = emptySet(),
    val forcedAspects: MutableSet<String> = mutableSetOf(),
    val choiceFeatures: MutableMap<String, String> = mutableMapOf(),
    val flagFeatures: MutableSet<String> = mutableSetOf(),
    val activeMoves: MutableList<MoveTemplate?> = MutableList(4) { null },
    val benchedMoves: MutableSet<MoveTemplate> = mutableSetOf()
) {
    companion object {
        fun defaultGenderFor(form: FormData): Gender {
            return when {
                form.maleRatio !in 0F..1F -> Gender.GENDERLESS
                form.maleRatio == 0F -> Gender.FEMALE
                else -> Gender.MALE
            }
        }
    }
}
