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
import com.cobblemon.mod.common.api.drop.DropEntry
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.getOrNull

object DropEntryMoLangFunctions : AbstractMoLangFunctionHolder<DropEntry> {
    override val holder: MutableList<(DropEntry) -> MutableMap<String, (MoParams) -> Any>> = mutableListOf(
        { dropEntry ->
            val map = hashMapOf<String, (MoParams) -> Any>()

            map["percentage"] = { DoubleValue(dropEntry.percentage.toDouble()) }
            map["quantity"] = { DoubleValue(dropEntry.quantity.toDouble()) }
            map["max_selectable_times"] = { DoubleValue(dropEntry.maxSelectableTimes.toDouble()) }
            map["can_drop"] = { params ->
                val pokemon = params.getOrNull<ObjectValue<Pokemon>>(0)?.obj
                DoubleValue(if (dropEntry.canDrop(pokemon)) 1.0 else 0.0)
            }
            map
        }
    )
}
