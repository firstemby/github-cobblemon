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

fun List<ExpressionOrEntityVariable>.asVariables() = mapNotNull { it.right().getOrNull() }


