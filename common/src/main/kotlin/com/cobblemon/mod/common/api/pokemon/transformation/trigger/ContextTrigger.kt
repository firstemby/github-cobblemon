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
 * @param TC The context that is serialized from JSON during species loading. This is what the [Context] provided
 * to [testTrigger] is expected to match against.
 *
 * @author Licious
 * @since March 19th, 2022
 */
interface ContextTrigger<TC>: TransformationTrigger {

    /**
     * Represents context provided for [TransformationTrigger] evaluation.
     *
     * @author Segfault Guy
     * @since October 15th, 2023
     */
    interface Context

    /** The target context required for this [Transformation] to be successfully triggered. */
    val requiredContext: TC

    /**
     * Checks if the given context is valid for the [requiredContext].
     *
     * @param context The [Context] given at runtime when querying the [Transformation].
     * @return If the context matched the [requiredContext].
     */
     fun testContext(context: Context): Boolean = false

    /**
     * Evaluates the triggering condition of a [Transformation].
     *
     * @param context The [Context] of the [Transformation].
     * @return Whether the triggering condition is satisfied. If [context] is null, bypasses the condition check.
     */
    override fun testTrigger(context: Context?) = context?.let { testContext(it) } ?: true

}