/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.adapters

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.pokeball.catching.CatchRateModifier
import com.cobblemon.mod.common.api.pokeball.catching.modifiers.BaseStatModifier
import com.cobblemon.mod.common.api.pokeball.catching.modifiers.CatchRateModifierAdapter
import com.cobblemon.mod.common.api.pokeball.catching.modifiers.GuaranteedModifier
import com.cobblemon.mod.common.api.pokeball.catching.modifiers.LabelModifier
import com.cobblemon.mod.common.api.pokeball.catching.modifiers.ExpressionModifier
import com.cobblemon.mod.common.api.pokeball.catching.modifiers.PropertyBasedModifier
import com.cobblemon.mod.common.api.pokeball.catching.modifiers.ScriptModifier
import com.cobblemon.mod.common.api.pokeball.catching.modifiers.SimpleModifier
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.writeString
import com.google.common.collect.HashBiMap
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonSerializationContext
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import java.lang.reflect.Type
import kotlin.reflect.KClass

object CobblemonCatchRateModifierAdapter : CatchRateModifierAdapter {
    private const val VARIANT = "variant"

    private val types = HashBiMap.create<String, KClass<out CatchRateModifier>>()

    init {
        this.registerType(SimpleModifier.VARIANT, SimpleModifier::class)
        this.registerType(BaseStatModifier.VARIANT, BaseStatModifier::class)
        this.registerType(GuaranteedModifier.VARIANT, GuaranteedModifier::class)
        this.registerType(PropertyBasedModifier.VARIANT, PropertyBasedModifier::class)
        this.registerType(LabelModifier.VARIANT, LabelModifier::class)
        this.registerType(ExpressionModifier.VARIANT, ExpressionModifier::class)
        this.registerType(ScriptModifier.VARIANT, ScriptModifier::class)
    }

    val gson: Gson by lazy {
        GsonBuilder()
            .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
            .registerTypeAdapter(Expression::class.java, ExpressionAdapter)
            .registerTypeAdapter(ExpressionLike::class.java, ExpressionLikeAdapter)
            .registerTypeAdapter(PokemonProperties::class.java, pokemonPropertiesShortAdapter)
            .registerTypeAdapter(CatchRateModifier::class.java, CobblemonCatchRateModifierAdapter)
            .setPrettyPrinting()
            .create()
    }

    override fun <T : CatchRateModifier> registerType(id: String, type: KClass<T>) {
        this.types[id.lowercase()] = type
    }

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext,
    ): CatchRateModifier {
        val variant =
            json.asJsonObject
                .get(VARIANT)
                .asString
                .lowercase()
        val type = getModifierType(variant)
        return context.deserialize(json, type.java)
    }

    override fun serialize(
        modifier: CatchRateModifier,
        typeOfSrc: Type,
        context: JsonSerializationContext,
    ): JsonElement {
        val json = context.serialize(modifier, modifier::class.java).asJsonObject
        val variant = getVariant(modifier)
        json.addProperty(VARIANT, variant)
        return json
    }

    fun encode(modifier: CatchRateModifier, buffer: FriendlyByteBuf) {
        buffer.writeString(gson.toJson(modifier, CatchRateModifier::class.java))
    }

    fun decode(buffer: FriendlyByteBuf): CatchRateModifier =
        gson.fromJson(buffer.readString(), CatchRateModifier::class.java)

    private fun getVariant(modifier: CatchRateModifier): String {
        val variant =
            this.types.inverse()[modifier::class]
                ?: throw IllegalArgumentException("Cannot resolve pokeball catchrate modifier for type ${modifier::class.qualifiedName}")

        return variant
    }

    private fun getModifierType(variant: String): KClass<out CatchRateModifier> {
        val requirementType =
            this.types[variant]
                ?: throw IllegalArgumentException("Cannot resolve pokeball catchrate modifier type for variant $variant")

        return requirementType
    }
}
