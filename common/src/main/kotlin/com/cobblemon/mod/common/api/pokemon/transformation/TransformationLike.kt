/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokemon.transformation

/**
 * Represents the base of a transformation.
 * It can either be the full data container present on the server side [Transformation].
 * Or the client side representation for display [TransformationDisplay].
 *
 * @author Licious
 * @since April 28th, 2022
 */
interface TransformationLike {

    /**
     * The unique id of the transformation.
     * It should be human-readable, I.E pikachu_level
     */
    val id: String

}