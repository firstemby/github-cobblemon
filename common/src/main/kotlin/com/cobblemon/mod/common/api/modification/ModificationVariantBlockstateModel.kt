/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.modification

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.Property

class ModificationVariantBlockstateModel(val conditions: Map<String, String>, val model: ResourceLocation) {
    fun matches(state: BlockState): Boolean = conditions.all { (propertyName, expectedValue) ->
        val property = state.block.stateDefinition.getProperty(propertyName) ?: return@all false
        propertyValueName(state, property) == expectedValue
    }

    fun encode(buffer: FriendlyByteBuf) {
        buffer.writeMap(conditions, { buf, key -> buf.writeUtf(key) }, { buf, value -> buf.writeUtf(value) })
        buffer.writeResourceLocation(model)
    }

    companion object {
        private const val CONDITION_SEPARATOR = ","
        private const val VALUE_SEPARATOR = "="

        fun parseConditions(raw: String): Map<String, String>? {
            val conditions = linkedMapOf<String, String>()
            raw.split(CONDITION_SEPARATOR).forEach { condition ->
                val parts = condition.split(VALUE_SEPARATOR)
                if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                    return null
                }
                conditions[parts[0].trim()] = parts[1].trim()
            }
            return conditions
        }

        fun decode(buffer: FriendlyByteBuf): ModificationVariantBlockstateModel {
            val conditions = buffer.readMap({ buf -> buf.readUtf() }, { buf -> buf.readUtf() })
            return ModificationVariantBlockstateModel(conditions, buffer.readResourceLocation())
        }

        @Suppress("UNCHECKED_CAST")
        private fun propertyValueName(state: BlockState, property: Property<*>): String {
            return valueName(state, property as Property<Comparable<Any>>)
        }

        private fun <T : Comparable<T>> valueName(state: BlockState, property: Property<T>): String = property.getName(state.getValue(property))
    }
}
