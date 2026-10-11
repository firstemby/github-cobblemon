/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.adapters

import com.cobblemon.mod.common.api.pokeball.catching.CaptureEffect
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.writeString
import com.google.common.collect.HashBiMap
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import net.minecraft.network.FriendlyByteBuf
import java.lang.reflect.Type
import kotlin.reflect.KClass

object CobblemonCaptureEffectAdapter : JsonDeserializer<CaptureEffect>, JsonSerializer<CaptureEffect> {
    private const val VARIANT = "variant"

    private val types = HashBiMap.create<String, KClass<out CaptureEffect>>()

    val gson: Gson by lazy {
        GsonBuilder()
            .registerTypeAdapter(CaptureEffect::class.java, CobblemonCaptureEffectAdapter)
            .setPrettyPrinting()
            .create()
    }

    fun <T : CaptureEffect> registerType(id: String, type: KClass<T>) {
        this.types[id.lowercase()] = type
    }

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext,
    ): CaptureEffect {
        val variant =
            json.asJsonObject
                .get(VARIANT)
                .asString
                .lowercase()
        val type = getModifierType(variant)
        return context.deserialize(json, type.java)
    }

    override fun serialize(
        effect: CaptureEffect,
        typeOfSrc: Type,
        context: JsonSerializationContext,
    ): JsonElement {
        val json = context.serialize(effect, effect::class.java).asJsonObject
        val variant = getVariant(effect)
        json.addProperty(VARIANT, variant)
        return json
    }

    fun encode(effect: CaptureEffect, buffer: FriendlyByteBuf) {
        buffer.writeString(gson.toJson(effect, CaptureEffect::class.java))
    }

    fun decode(buffer: FriendlyByteBuf): CaptureEffect = gson.fromJson(buffer.readString(), CaptureEffect::class.java)

    private fun getVariant(effect: CaptureEffect): String {
        val variant =
            this.types.inverse()[effect::class]
                ?: throw IllegalArgumentException("Cannot resolve pokeball capture effect for type ${effect::class.qualifiedName}")

        return variant
    }

    private fun getModifierType(variant: String): KClass<out CaptureEffect> {
        val effectType =
            this.types[variant] ?: throw IllegalArgumentException("Cannot resolve pokeball capture effect type for variant $variant")

        return effectType
    }
}
