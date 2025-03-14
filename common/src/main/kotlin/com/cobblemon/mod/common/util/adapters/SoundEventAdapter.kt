/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.adapters

import com.google.gson.*
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import java.lang.reflect.Type

/**
 * Adapter for deserializing SoundEvent types.
 *
 * @author Jackowes
 * @since March 5th, 2025
 */

object SoundEventAdapter : JsonDeserializer<SoundEvent> {
    override fun deserialize(json: JsonElement, type: Type, context: JsonDeserializationContext): SoundEvent {
        val soundId = json.asString

        // Attempt to grab the sound resource location. If incorrect then return null
        val resourceLocation = ResourceLocation.read(soundId).result().orElse(null)
            ?: throw JsonParseException("Invalid sound event resource location: $soundId")

        // Attempt to retrieve the SoundEvent from the registry
        return BuiltInRegistries.SOUND_EVENT.getOptional(resourceLocation)
            .orElseThrow { JsonParseException("Unknown sound event: $soundId") }
    }
}