/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.mojang.datafixers.util.Either
import kotlin.jvm.optionals.getOrNull

typealias ExpressionOrEntityVariable = Either<Expression, MoLangConfigVariable>

/**
 * In the case of [ExpressionOrEntityVariable] the right side is the only one that requires visibility for a user,
 * this function is a shorthand for finding all of the values in the list that have something to show the user to
 * potentially edit.
 */
fun List<ExpressionOrEntityVariable>.asVariables() = mapNotNull { it.right().getOrNull() }


