/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.events.pokemon.form

import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.moLangFunctionMap
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.form.TemporaryForm
import com.cobblemon.mod.common.util.getBoolean

/**
 * An event fired when [TemporaryForm.test] has been performed.
 * The final state [result] will be the return value of the test function.
 *
 * @property pokemon The [Pokemon] being tested against.
 * @property form The [TemporaryForm] instance performing the test.
 * @property originalResult The base result from the Cobblemon implementation.
 * @property result The final value returned by [TemporaryForm.test].
 */
data class FormTestedEvent(
    override val pokemon: Pokemon,
    override val form: TemporaryForm,
    val originalResult: Boolean,
) : FormEvent {
    var result: Boolean = originalResult

    val context = mutableMapOf(
        "pokemon" to pokemon.struct,
        "form" to StringValue(form.formOnlyShowdownId()),
        "original_result" to DoubleValue(originalResult),
    )

    val functions = moLangFunctionMap(
        "result" to { DoubleValue(result) },
        "set_result" to {
            result = it.getBoolean(0)
            DoubleValue.ONE
        }
    )
}
