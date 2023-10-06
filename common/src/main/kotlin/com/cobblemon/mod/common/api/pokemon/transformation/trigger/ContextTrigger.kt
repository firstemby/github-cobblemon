/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokemon.transformation.trigger

import com.cobblemon.mod.common.api.pokemon.transformation.Transformation
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.triggers.ItemInteractionTrigger
import com.cobblemon.mod.common.pokemon.transformation.triggers.TradeTrigger

/**
 * Represents a [TransformationTrigger] of a [Pokemon] that can only occur during specific actions and with added context.
 * For the default implementations see [ItemInteractionTrigger] & [TradeTrigger].
 *
 * @param RC The context given at runtime when querying the [Transformation].
 * @param TC The context that is serialized from JSON during species loading, this is what the [RC] is expected to match against.
 * @author Licious
 * @since March 19th, 2022
 */
interface ContextTrigger<RC, TC>: TransformationTrigger {

    /**
     * The target context for this [Transformation] to even be tested.
     */
    val requiredContext: TC

    /**
     * Checks if the given context is valid for the [requiredContext].
     *
     * @param pokemon The [Pokemon] attempting to transform.
     * @param context The context of this query.
     * @return If the context matched the [requiredContext].
     */
    fun testContext(pokemon: Pokemon, context: RC): Boolean

}