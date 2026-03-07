/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.adapters

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

/**
 * A Gson [TypeAdapter] that serializes/deserializes enums using lowercase names.
 */
class LowercaseEnumAdapter<T : Enum<T>>(private val enumClass: Class<T>) : TypeAdapter<T>() {
    private val nameToValue: Map<String, T> = enumClass.enumConstants.associateBy { it.name.lowercase() }
    private val valueToName: Map<T, String> = enumClass.enumConstants.associateWith { it.name.lowercase() }

    override fun write(out: JsonWriter, value: T?) {
        if (value == null) {
            out.nullValue()
        } else {
            out.value(valueToName[value])
        }
    }

    override fun read(reader: JsonReader): T? {
        val name = reader.nextString()
        return nameToValue[name.lowercase()]
            ?: throw IllegalArgumentException("Unknown ${enumClass.simpleName} value: $name. Valid values: ${nameToValue.keys}")
    }

    companion object {
        /**
         * Creates a lowercase enum adapter for the specified enum class.
         */
        inline fun <reified T : Enum<T>> create(): LowercaseEnumAdapter<T> {
            return LowercaseEnumAdapter(T::class.java)
        }
    }
}
