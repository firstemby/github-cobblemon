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
import com.cobblemon.mod.common.pokemon.transformation.form.TemporaryForm

/**
 * Fired after a temporary form change finishes.
 *
 * @param pokemon The [Pokemon] resulting from the form change.
 * @param form The [TemporaryForm] that was used.
 *
 * @author Hiroku
 * @since June 3rd, 2026
 */
data class FormCompleteEvent(
    /** The [Pokemon] that did the form change. */
    override val pokemon: Pokemon,
    /** The [TemporaryForm] that was used. */
    override val form: TemporaryForm
) : FormEvent {
    val context = mutableMapOf(
        "pokemon" to pokemon.struct,
        "form" to StringValue(form.formOnlyShowdownId())
    )
}
