/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokemon.transformation.controller

import com.cobblemon.mod.common.api.pokemon.transformation.TransformationLike
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.controller.ClientTransformationController
import com.cobblemon.mod.common.pokemon.transformation.controller.ServerTransformationController

/**
 * Responsible for holding all available [TransformationLike]s in the [Pokemon].
 * Also handles all the networking behind them.
 * For the Cobblemon default implementations see [ClientTransformationController] & [ServerTransformationController].
 *
 * @author Licious
 * @since April 28th, 2022
 */
interface TransformationController<T : TransformationLike> : MutableSet<T> {
    /**
     * Guard for [start] to prevent a transformation from executing while the [pokemon] is busy or if the transformation
     * is not in the controller.
     *
     * @param transformation the [TransformationLike] to validate.
     * @return whether this controller is capable of starting the transformation.
     */
    fun canStart(transformation: T): Boolean

    /**
     * Whether this controller is capable of executing [start] for a transformation. This checks if the [pokemon] is busy
     * or if there is a transformation ready in the controller.
     *
     * @return whether this controller is capable of starting a transformation.
     */
    fun canStart(): Boolean

    /**
     * Validates the [TransformationLike] currently active for this controller's [pokemon] and determines whether it can be
     * reverted and if the pokemon is capable of reverting right now.
     *
     * @return whether the transformation can be reverted.
     */
    fun canRevert(): Boolean

    /**
     * Retrieves the [TransformationLike] in this controller that matches the provided id.
     *
     * @param id the identifier of the transformation.
     * @return the transformation matching this id.
     */
    fun getById(id: String) = this.firstOrNull { transformation -> transformation.id.equals(id, true) }
}