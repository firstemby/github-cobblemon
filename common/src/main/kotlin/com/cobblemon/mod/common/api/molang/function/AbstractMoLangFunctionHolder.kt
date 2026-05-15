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
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.resources.ResourceLocation

typealias MoFn = (MoParams) -> Any
typealias FunctionProvider<T> = (T) -> Map<String, MoFn>

/**
 * Abstract MoLang function holder
 * Effectively allows to attach molang functions to almost anything by extending it
 * Handles potential third-party MoLang function collision 
 *
 * @sample BattleActorMoLangFunctions
 * @author Gito, Amo Aster
 */
abstract class AbstractMoLangFunctionHolder<T> {

    data class CustomFunctionProvider<T>(
        val source: ResourceLocation,
        val provider: FunctionProvider<T>
    )

    private val custom: MutableList<CustomFunctionProvider<T>> = mutableListOf()

    protected abstract fun T.moLangFunctions(): MutableMap<String, MoFn>
    protected open fun onCollision(name: String, existingSource: ResourceLocation, newSource: ResourceLocation) {}

    fun attach(value: T): Map<String, MoFn> {
        val functions = value.moLangFunctions()
        val ownerByName = functions.keys.associateWith { cobblemonResource("molang") }.toMutableMap()

        custom.forEach { customFunctionProvider ->
            val incoming = customFunctionProvider.provider(value)
            for ((name, fn) in incoming) {
                val existingSource = ownerByName[name]
                if (existingSource == null) {
                    functions[name] = fn
                    ownerByName[name] = customFunctionProvider.source
                    continue
                }

                onCollision(name, existingSource, customFunctionProvider.source)
                Cobblemon.LOGGER.error("MoLang function collision: '$name' from '${customFunctionProvider.source}' conflicts with '$existingSource'")
            }
        }

        return functions
    }

    fun add(source: ResourceLocation = cobblemonResource("unknown"), definition: FunctionProvider<T>) {
        custom += CustomFunctionProvider(source, definition)
    }

    fun add(name: String, source: ResourceLocation = cobblemonResource("unknown"), function: MoFn) {
        add(source = source) { mapOf(name to function) }
    }
}