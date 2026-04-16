/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.adapters

import com.google.gson.Gson
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.util.Locale

class LowerCaseEnumTypeAdapterFactory : TypeAdapterFactory {
    override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        val rawType = type.rawType
        if (!rawType.isEnum) {
            return null
        }

        val constants = rawType.enumConstants.associateBy {
            (it as Enum<*>).name.lowercase(Locale.ROOT)
        }

        return object : TypeAdapter<T>() {
            override fun write(out: JsonWriter, value: T?) {
                if (value == null) {
                    out.nullValue()
                    return
                }

                out.value((value as Enum<*>).name.lowercase(Locale.ROOT))
            }

            @Suppress("UNCHECKED_CAST")
            override fun read(reader: JsonReader): T? {
                if (reader.peek() == JsonToken.NULL) {
                    reader.nextNull()
                    return null
                }

                return constants[reader.nextString().lowercase(Locale.ROOT)] as T?
            }
        }
    }
}
