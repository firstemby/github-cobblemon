/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokemon.transformation.adapters

import com.cobblemon.mod.common.api.pokemon.transformation.requirement.TransformationRequirement
import com.cobblemon.mod.common.pokemon.transformation.adapters.CobblemonRequirementAdapter
import com.google.gson.JsonDeserializer
import com.google.gson.JsonSerializer
import kotlin.reflect.KClass

/**
 * Saves and loads [TransformationRequirement]s with JSON.
 * For the default implementation see [CobblemonRequirementAdapter].
 *
 * @author Licious
 * @since March 21st, 2022
 */
interface RequirementAdapter : JsonDeserializer<TransformationRequirement>, JsonSerializer<TransformationRequirement> {

    /**
     * Registers the given type of [TransformationRequirement] to it's associated ID for deserialization.
     *
     * @param T The type of [TransformationRequirement].
     * @param id The id of the evolution event.
     * @param type The [KClass] of the type.
     */
    fun <T : TransformationRequirement> registerType(id: String, type: KClass<T>)

}