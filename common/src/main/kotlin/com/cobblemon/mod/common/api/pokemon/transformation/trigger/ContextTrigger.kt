/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokemon.transformation.trigger

import com.cobblemon.mod.common.api.pokemon.transformation.Transformation
import com.cobblemon.mod.common.item.interactive.LinkCableItem
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.triggers.ItemInteractionTrigger
import com.cobblemon.mod.common.pokemon.transformation.triggers.TradeTrigger

/**
 * Represents a [TransformationTrigger] of a [Pokemon] that can only occur during specific actions and with added context.
 * For the default implementations see [ItemInteractionTrigger] & [TradeTrigger].
 *
 * @param TC The context that is serialized from JSON during species loading, this is what the [RC] is expected to match against.
 * @author Licious
 * @since March 19th, 2022
 */
interface ContextTrigger<TC>: TransformationTrigger {

    /**
     * The target context for this [Transformation] to even be tested.
     */
    val requiredContext: TC

    /**
     * Checks if the given context is valid for the [requiredContext].
     *
     * @param pokemon The [Pokemon] attempting to transform.
     * @param context The [TriggerContext] given at runtime when querying the [Transformation].
     * @return If the context matched the [requiredContext].
     */
    fun testContext(context: TriggerContext): Boolean = false

    /**
     * Evaluates the triggering condition of a [Transformation].
     *
     * @param context The [TriggerContext] of the [Transformation]. If null, bypasses the condition check (see [LinkCableItem]).
     * @return Whether the triggering condition is satisfied.
     */
    override fun testTrigger(context: TriggerContext?) = context?.let { testContext(it) } ?: true

}