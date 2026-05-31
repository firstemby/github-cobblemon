/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokemon.aspect

import com.cobblemon.mod.common.api.pokemon.feature.SpeciesFeature
import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * A provider of aspects for a [SpeciesFeature].
 *
 * @author Segfault Guy
 * @since Feb 12th, 2024
 */
interface FeatureAspectProvider: AspectProvider {
    var default: String?
    var isAspect: Boolean

    /** Returns true if this [aspect] is provided by this provider. */
    fun matches(aspect: String): Boolean

    /** Creates a [SpeciesFeature] that provides the respective [aspect]. */
    fun from(aspect: String): SpeciesFeature<*>?

    /** Sets the [aspect] that's provided. */
    fun set(pokemon: Pokemon, aspect: String)
}