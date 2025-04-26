/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.adapters

import com.cobblemon.mod.common.Cobblemon
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type
import java.time.ZoneId
import java.time.ZoneOffset


/**
 * A type adapter for [ZoneOffset]s.
 * This will never fail to deserialize and will instead default to the System's default time zone and log an error message.
 *
 * @author JPAK
 * @since December 26, 2024
 */
object ZoneIdAdapter: JsonSerializer<ZoneId>, JsonDeserializer<ZoneId>  {
    override fun serialize(
        src: ZoneId,
        typeOfSrc: Type,
        context: JsonSerializationContext
    ): JsonElement {
        return JsonPrimitive(src.id)
    }

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): ZoneId {
        return try {
            ZoneId.of(json.asString)
        } catch (_: Exception) {
            Cobblemon.LOGGER.error("Failed to load ZoneId from the ID {}, defaulting to the system time zone", json.asString)
            ZoneOffset.systemDefault()
        }
    }
}