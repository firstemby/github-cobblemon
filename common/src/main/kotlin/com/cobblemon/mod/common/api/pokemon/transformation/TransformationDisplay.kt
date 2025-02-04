/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokemon.transformation

import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species

/**
 * Represents a transformation of a [Pokemon], this is the client side counterpart of [Transformation].
 * This has no attachments to any data regarding the transformation itself and only serves for display purposes and basic communication.
 *
 * @author Licious
 * @since April 28th, 2022
 */
interface TransformationDisplay : TransformationLike {

    /**
     * The [Species] of the transformation result.
     */
    val species: Species

    /**
     * The aspects of the transformation result.
     * These are used by the client to resolve the entity it needs to display.
     */
    val aspects: Set<String>
}