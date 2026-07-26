/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.adapters

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

/**
 * A simple adapter that maps string values to objects based on a provided mapping.
 *
 * I could swear I already made something like this before.
 *
 * @author Hiroku
 * @since December 5th, 2025
 */
class StringToObjectAdapter<T>(
    val mapping: MutableMap<String, T>
) : JsonDeserializer<T> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): T = mapping[json.asString]
        ?: throw IllegalArgumentException("Unknown mapping for string: ${json.asString}")
}