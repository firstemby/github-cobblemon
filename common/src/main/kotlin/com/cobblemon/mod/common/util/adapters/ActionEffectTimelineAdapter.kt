/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.adapters

import com.cobblemon.mod.common.api.moves.animations.ActionEffectTimeline
import com.cobblemon.mod.common.api.moves.animations.ActionEffects
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

/**
 * Adapter for deserializing a reference to an [ActionEffectTimeline] from its identifier.
 *
 * @author Hiroku
 * @since August 31st, 2025
 */
object ActionEffectTimelineAdapter : JsonDeserializer<ActionEffectTimeline> {
    override fun deserialize(json: JsonElement, type: Type, ctx: JsonDeserializationContext): ActionEffectTimeline {
        val identifier = json.asString.asIdentifierDefaultingNamespace()
        val timeline = ActionEffects.actionEffects[identifier]
        return timeline ?: throw IllegalArgumentException("No ActionEffectTimeline found for id $identifier")
    }
}