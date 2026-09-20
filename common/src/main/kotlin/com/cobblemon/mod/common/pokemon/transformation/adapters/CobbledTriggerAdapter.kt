/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.adapters

import com.cobblemon.mod.common.api.pokemon.transformation.adapters.TriggerAdapter
import com.cobblemon.mod.common.api.pokemon.transformation.trigger.TransformationTrigger
import com.cobblemon.mod.common.pokemon.transformation.triggers.BlockClickTrigger
import com.cobblemon.mod.common.pokemon.transformation.triggers.ItemInteractionTrigger
import com.cobblemon.mod.common.pokemon.transformation.triggers.LevelUpTrigger
import com.cobblemon.mod.common.pokemon.transformation.triggers.TradeTrigger
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonSerializationContext
import java.lang.reflect.Type
import kotlin.reflect.KClass

/**
 * The default implementation of [TriggerAdapter].
 *
 * @author Licious
 * @since March 20th, 2022
 */
object CobblemonTriggerAdapter : TriggerAdapter {

    private const val VARIANT = "variant"

    private val types = mutableMapOf<String, KClass<out TransformationTrigger>>()

    init {
        registerType(LevelUpTrigger.ADAPTER_VARIANT, LevelUpTrigger::class)
        registerType(TradeTrigger.ADAPTER_VARIANT, TradeTrigger::class)
        registerType(ItemInteractionTrigger.ADAPTER_VARIANT, ItemInteractionTrigger::class)
        registerType(LevelUpTrigger.ALTERNATIVE_ADAPTER_VARIANT, LevelUpTrigger::class)
        registerType(BlockClickTrigger.ADAPTER_VARIANT, BlockClickTrigger::class)
    }

    override fun <T : TransformationTrigger> registerType(id: String, type: KClass<T>) {
        types[id.lowercase()] = type
    }

    override fun deserialize(jsonIn: JsonElement, typeOfT: Type, context: JsonDeserializationContext): TransformationTrigger {
        val json = jsonIn.asJsonObject
        val variant = json.get(VARIANT).asString.lowercase()
        val type = types[variant] ?: throw IllegalArgumentException("Cannot resolve type for variant $variant")
        return context.deserialize(json, type.java)
    }

    override fun serialize(src: TransformationTrigger, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val json = context.serialize(src, src::class.java).asJsonObject
        val variant = types.entries.find { it.value == src::class }?.key ?: throw IllegalArgumentException("Cannot resolve variant for type ${src::class.qualifiedName}")
        json.addProperty(VARIANT, variant)
        return json
    }

}