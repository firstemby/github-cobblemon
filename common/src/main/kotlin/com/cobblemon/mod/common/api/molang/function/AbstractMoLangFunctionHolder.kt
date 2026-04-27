/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molang.function

import com.bedrockk.molang.runtime.MoParams
import com.cobblemon.mod.common.Cobblemon


typealias MoFn = (MoParams) -> Any
typealias Provider<T> = (T) -> Map<String, MoFn>

/**
 * Abstract MoLang function holder
 * Effectively allows to attach molang functions to almost anything by extending it
 * Handles potential third-party MoLang function collision 
 *
 * @sample BattleActorMoLangFunctions
 * @author Gito, Amo Aster
 */
abstract class AbstractMoLangFunctionHolder<T> {

    data class CustomProvider<T>(
        val source: String,
        val provider: Provider<T>
    )

    private val custom: MutableList<CustomProvider<T>> = mutableListOf()

    protected abstract fun T.moLangFunctions(): MutableMap<String, MoFn>
    protected open fun onCollision(name: String, existingSource: String, newSource: String) {}

    fun attach(value: T): Map<String, MoFn> {
        val functions = value.moLangFunctions()
        val ownerByName = functions.keys.associateWith { "cobblemon" }.toMutableMap()

        custom.forEach { customProvider ->
            val incoming = customProvider.provider(value)
            for ((name, fn) in incoming) {
                val existingSource = ownerByName[name]
                if (existingSource == null) {
                    functions[name] = fn
                    ownerByName[name] = customProvider.source
                    continue
                }

                onCollision(name, existingSource, customProvider.source)
                Cobblemon.LOGGER.error("MoLang function collision: '$name' from '${customProvider.source}' conflicts with '$existingSource'")
            }
        }

        return functions
    }

    fun add(source: String = "unknown", definition: Provider<T>) {
        custom += CustomProvider(source, definition)
    }

    fun add(name: String, source: String = "unknown", function: MoFn) {
        add(source) { mapOf(name to function) }
    }
}