/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.adapters

import com.cobblemon.mod.common.CobblemonMovesetBuilders
import com.cobblemon.mod.common.api.moves.MovesetBuilder
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

object MovesetBuilderReferenceAdapter : JsonDeserializer<MovesetBuilder> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): MovesetBuilder {
        return CobblemonMovesetBuilders.getOrThrow(json.asString.asIdentifierDefaultingNamespace())
    }
}