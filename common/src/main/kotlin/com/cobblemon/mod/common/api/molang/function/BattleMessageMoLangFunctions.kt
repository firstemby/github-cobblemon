/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molang.function

import com.bedrockk.molang.runtime.MoParams
import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage
import com.cobblemon.mod.common.util.getStringOrNull

object BattleMessageMoLangFunctions : AbstractMoLangFunctionHolder<BattleMessage>() {
    override fun BattleMessage.moLangFunctions(): MutableMap<String, (MoParams) -> Any> {
        val battleMessage = this
        val functions = hashMapOf<String, (MoParams) -> Any>()

        functions["has_argument_at"] = { params ->
            val index = params.getInt(0)
            val value = params.getStringOrNull(1) ?: ""
            DoubleValue(value == battleMessage.argumentAt(index))
        }
        functions["has_argument"] = { params ->
            val argumentName = params.getString(0)
            val value = params.getStringOrNull(1) ?: ""
            DoubleValue(value == battleMessage.optionalArgument(argumentName))
        }

        return functions
    }
}
