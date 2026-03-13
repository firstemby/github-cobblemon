/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molang.function

import com.bedrockk.molang.runtime.MoParams


/**
 * Abstract MoLang function holder
 * Effectively allows to attach molang functions to almost anything by extending it
 *
 * @sample BattleActorMoLangFunctions
 * @author Gito
 */
abstract class AbstractMoLangFunctionHolder<T> {
    val custom: MutableList<(T) -> Map<String, (MoParams) -> Any>> = mutableListOf()

    protected abstract fun T.moLangFunctions(): MutableMap<String, (MoParams) -> Any>

    fun attach(value: T): Map<String, (MoParams) -> Any> {
        return value.moLangFunctions().also { functions ->
            custom.forEach { definition ->
                functions.putAll(definition(value))
            }
        }
    }
}
