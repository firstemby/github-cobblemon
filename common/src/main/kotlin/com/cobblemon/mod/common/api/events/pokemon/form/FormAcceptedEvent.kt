/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.events.pokemon.form

import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.api.events.Cancelable
import com.cobblemon.mod.common.api.molang.MoLangFunctions.moLangFunctionMap
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.form.TemporaryForm

/**
 * Fired when a temporary form change is accepted.
 * Cancelling will not notify users nor remove the form from the pending list.
 *
 * @param pokemon The [Pokemon] about to change form.
 * @param form The [TemporaryForm] being used.
 *
 * @author Hiroku
 * @since June 3rd, 2026
 */
data class FormAcceptedEvent(
    override val pokemon: Pokemon,
    override val form: TemporaryForm
) : Cancelable(), FormEvent {
    val context = mutableMapOf(
        "pokemon" to pokemon.struct,
        "form" to StringValue(form.formOnlyShowdownId())
    )

    val functions = moLangFunctionMap(cancelFunc)
}
