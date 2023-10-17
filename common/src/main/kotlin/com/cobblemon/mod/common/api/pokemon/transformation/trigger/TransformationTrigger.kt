/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokemon.transformation.trigger

import com.cobblemon.mod.common.api.pokemon.transformation.Transformation

/**
 * Represents a condition that starts a [Transformation].
 *
 * @author Segfault Guy
 * @since Sept 8th, 2023
 */
interface TransformationTrigger {

    /**
     * Evaluates the triggering condition of a [Transformation].
     *
     * @param context The optional [TriggerContext] needed to transform.
     * @return Whether the triggering condition is satisfied.
     */
    fun testTrigger(context: TriggerContext?): Boolean = true

}

/**
 * Represents context that's needed for [TransformationTrigger] evaluation.
 *
 * @author Segfault Guy
 * @since October 15th, 2023
 */
interface TriggerContext