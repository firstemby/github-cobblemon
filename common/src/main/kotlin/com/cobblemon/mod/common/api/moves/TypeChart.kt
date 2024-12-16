/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.moves

import com.cobblemon.mod.common.api.types.ElementalType
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.battles.runner.ShowdownService

object TypeChart {
    private val movesEffectiveness = mutableMapOf<ElementalType, MutableMap<ElementalType, MoveEffectiveness>>()

    fun load() {
        movesEffectiveness.clear()

        val typeChartJson = ShowdownService.service.getTypeChart()
        typeChartJson.keySet().forEach { type ->
            val defenderType = ElementalTypes.get(type) ?: return@forEach

            val moveTypes = typeChartJson.get(type).asJsonObject.get("damageTaken").asJsonObject
            moveTypes.keySet().forEach innerLoop@ {
                val moveType = ElementalTypes.get(it) ?: return@innerLoop
                val moveEffectiveness = moveTypes.get(it).asInt

                val defenderMap = movesEffectiveness.getOrPut(moveType) { mutableMapOf() }
                defenderMap[defenderType] = MoveEffectiveness.of(moveEffectiveness)
            }
        }
    }

    fun getMoveEffectiveness(moveType: ElementalType, defenderTypes: List<ElementalType?>): Double {
        var multiplier = 1.0
        defenderTypes.filterNotNull().forEach {
            multiplier *= getMoveEffectiveness(moveType, it).multiplier
        }

        return multiplier
    }

    private fun getMoveEffectiveness(moveType: ElementalType, defenderType: ElementalType): MoveEffectiveness {
        return movesEffectiveness[moveType]?.get(defenderType) ?: throw IllegalArgumentException("Unknown effectiveness type: $moveType $defenderType")
    }
}