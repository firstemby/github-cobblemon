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
import com.cobblemon.mod.common.pokeball.PokeBall
import com.cobblemon.mod.common.util.getStringOrNull

object PokeBallMoLangFunctions : AbstractMoLangFunctionHolder<PokeBall>() {
    override fun PokeBall.moLangFunctions(): MutableMap<String, (MoParams) -> Any> {
        val ball = this
        val functions = hashMapOf<String, (MoParams) -> Any>()


        return functions
    }
}
