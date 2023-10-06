/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokemon.transformation.adapters

import com.cobblemon.mod.common.api.pokemon.transformation.trigger.TransformationTrigger
import com.cobblemon.mod.common.pokemon.transformation.adapters.CobblemonTriggerAdapter
import com.google.gson.JsonDeserializer
import com.google.gson.JsonSerializer
import kotlin.reflect.KClass

/**
 * Saves and loads [TransformationTrigger]s with JSON.
 * For the default implementation see [CobblemonTriggerAdapter].
 *
 * @author Licious
 * @since March 20th, 2022
 */
interface TriggerAdapter : JsonDeserializer<TransformationTrigger>, JsonSerializer<TransformationTrigger> {

    /**
     * Registers the given type of [TransformationTrigger] to it's associated ID for deserialization.
     *
     * @param T The type of [TransformationTrigger].
     * @param id The id of the evolution event.
     * @param type The [KClass] of the type.
     */
    fun <T : TransformationTrigger> registerType(id: String, type: KClass<T>)

}