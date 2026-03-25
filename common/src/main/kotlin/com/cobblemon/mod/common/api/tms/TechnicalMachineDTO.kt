/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.tms

import com.cobblemon.mod.common.api.moves.MoveTemplate

/**
 * DTO used for [TechnicalMachine] JSON loading because Gson cannot deserialize Minecraft Ingredients.
 */
data class TechnicalMachineDTO(
    val moveName: MoveTemplate,
    val recipe: List<TechnicalMachineRecipeDTO>?,
    val obtainMethods: List<ObtainMethod> = emptyList(),
    val type: String
) {
    fun toTechnicalMachine(): TechnicalMachine {
        return TechnicalMachine(
            moveName = moveName,
            recipe = recipe?.map { it.toTechnicalMachineRecipe() },
            obtainMethods = obtainMethods,
            type = type
        )
    }
}