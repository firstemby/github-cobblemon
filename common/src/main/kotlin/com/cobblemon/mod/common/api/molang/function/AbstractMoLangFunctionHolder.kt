/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molang.function

import com.bedrockk.molang.runtime.MoParams
import com.bedrockk.molang.runtime.struct.QueryStruct
import com.cobblemon.mod.common.Cobblemon
import net.minecraft.resources.ResourceLocation

typealias MoFn = (MoParams) -> Any
typealias FunctionProvider<T> = (T) -> Map<ResourceLocation, MoFn>

/**
 * Abstract MoLang function holder
 * Effectively allows to attach molang functions to almost anything by extending it
 * Handles potential third-party MoLang function collision
 * Functions are identified by a [ResourceLocation] where namespace = source mod, path = function name, and then
 * flattened into a per-namespace struct.
 * e.g. "somemod:player_data" flattens to "q.somemod.player_data()"
 *
 * @sample BattleActorMoLangFunctions
 * @author Gito, Amo Aster
 */
abstract class AbstractMoLangFunctionHolder<T> {

    private val custom: MutableList<FunctionProvider<T>> = mutableListOf()

    protected abstract fun T.moLangFunctions(): MutableMap<String, MoFn>
    protected open fun onCollision(id: ResourceLocation, existingNamespace: String, newNamespace: String) {}

    fun attach(value: T): Map<String, MoFn> {
        val functions = value.moLangFunctions()
        val functionsById = functions.entries.associate { (name, fn) ->
            ResourceLocation.fromNamespaceAndPath(Cobblemon.MODID, name) to fn
        }.toMutableMap()
        val ownerByName = functionsById.keys.associateWith { it.namespace }.toMutableMap()

        custom.forEach { provider ->
            val incoming = provider(value)
            for ((id, fn) in incoming) {
                val existingNamespace = ownerByName[id]
                if (existingNamespace == null) {
                    functionsById[id] = fn
                    ownerByName[id] = id.namespace
                    continue
                }

                onCollision(id, existingNamespace, id.namespace)
                Cobblemon.LOGGER.error("MoLang function collision: '$id' from '${id.namespace}' conflicts with '$existingNamespace'")
            }
        }

        val rootFunctions = linkedMapOf<String, MoFn>()
        val namespacedStructs = mutableMapOf<String, QueryStruct>()
        val canonicalBySanitized = mutableMapOf<Pair<String, String>, ResourceLocation>()

        for ((id, fn) in functionsById) {
            val sanitizedName = sanitizeMoLangPath(id.path)
            val key = id.namespace to sanitizedName
            val existing = canonicalBySanitized[key]

            if (existing != null && existing != id) {
                Cobblemon.LOGGER.error("MoLang function collision after sanitization: '$id' and '$existing' both map to '${id.namespace}.$sanitizedName'")
                continue
            }

            canonicalBySanitized[key] = id

            if (id.namespace == Cobblemon.MODID) {
                rootFunctions[sanitizedName] = fn
                continue
            }

            val namespaceStruct = namespacedStructs.getOrPut(id.namespace) { QueryStruct(hashMapOf()) }
            namespaceStruct.addFunction(sanitizedName, fn)
        }

        return buildMap {
            rootFunctions.forEach { (name, fn) -> put(name, fn) }
            namespacedStructs.forEach { (namespace, struct) -> put(namespace, { struct }) }
        }
    }

    private fun sanitizeMoLangPath(path: String): String {
        val sanitized = path
            .lowercase()
            .replace('-', '_')
            .replace('/', '_')
            .replace('.', '_')
            .replace(Regex("[^a-z0-9_]"), "_")

        val startsWithDigit = sanitized.firstOrNull()?.isDigit() == true
        val normalized = if (startsWithDigit) "_$sanitized" else sanitized
        return normalized.ifEmpty { "fn" }
    }

    fun add(provider: FunctionProvider<T>) {
        custom += provider
    }

    fun add(id: ResourceLocation, function: MoFn) {
        add { mapOf(id to function) }
    }
}