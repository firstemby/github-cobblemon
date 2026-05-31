/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.summary.widgets.screens.transformations

import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.form.FormDisplay
import com.cobblemon.mod.common.pokemon.transformation.form.TemporaryForm
import com.cobblemon.mod.common.util.lang

class FormSelectScreen(
    x: Int,
    y: Int,
    pokemon: Pokemon
) : EvolutionSelectScreen<FormDisplay, TemporaryForm>(x, y, lang("ui.forms"), pokemon) {

    override fun getSlots(pokemon: Pokemon): Collection<TransformationSlot<FormDisplay, TemporaryForm>> {
        return pokemon.formProxy.client().map { FormSlot(pokemon, it) }
    }

    class FormSlot(pokemon: Pokemon, display: FormDisplay) : TransformationSlot<FormDisplay, TemporaryForm>(pokemon, display, lang("ui.form")) {
        override fun getProxy(pokemon: Pokemon) = pokemon.formProxy
    }
}