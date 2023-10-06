/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokemon.transformation.requirement

import com.cobblemon.mod.common.api.pokemon.transformation.Transformation
import com.cobblemon.mod.common.api.pokemon.transformation.trigger.ContextTrigger
import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * Represents a requirement for a transformation.
 * Requirements are constant and will never change regardless of the backing action.
 *
 * See [Transformation.requirements] & [Transformation.test] for usage.
 *
 * @author Licious
 * @since March 19th, 2022
 */
fun interface TransformationRequirement {

    /**
     * Checks if the given [Pokemon] satisfies the requirement.
     *
     * @param pokemon The [Pokemon] being queried.
     * @return If the requirement was satisfied.
     */
    fun check(pokemon: Pokemon): Boolean

    /**
     * Callback after the [Transformation] has been performed.
     *
     * @param pokemon The [Pokemon] being queried.
     */
    fun fulfill(pokemon: Pokemon) {}
}