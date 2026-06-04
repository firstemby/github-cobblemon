/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.events.pokemon.form

import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.form.FormDisplay
import com.cobblemon.mod.common.pokemon.transformation.form.TemporaryForm

/**
 * Fired before a form is sent to clients. For modifications on the actual output when they accept it modify
 * the [display] directly.
 *
 * @param pokemon The [Pokemon] that will change form if this proposition is accepted.
 * @param display The current [FormDisplay] the client will see.
 * @param form The [TemporaryForm] being used.
 *
 * @author Hiroku
 * @since June 3rd, 2026
 */
data class FormDisplayEvent(
    override val pokemon: Pokemon,
    var display: FormDisplay,
    override val form: TemporaryForm
) : FormEvent {
    val context = mutableMapOf(
        "pokemon" to pokemon.struct,
        "display" to display.struct,
        "form" to StringValue(form.formOnlyShowdownId())
    )
}
