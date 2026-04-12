/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.breeding.rule

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.struct.QueryStruct
import com.bedrockk.molang.runtime.value.DoubleValue

/**
 * Parent interface for inputs into breeding rules. This is mainly for convenience and not having to repeat
 * code too often, not that it has some critical kind of functional role.
 *
 * @author Hiroku
 * @since April 3rd, 2026
 */
interface BreedingRuleInputs {
    val runtime: MoLangRuntime
    /**
     * True when breeding is being done without the intention of creating a state-change. When true all the rules
     * that change things on the parents or server should... not do that. Just skip it.
     */
    val isSimulated: Boolean
    fun addToStruct(struct: QueryStruct) {
        struct.addFunction("is_simulated") { DoubleValue(isSimulated) }
    }
}