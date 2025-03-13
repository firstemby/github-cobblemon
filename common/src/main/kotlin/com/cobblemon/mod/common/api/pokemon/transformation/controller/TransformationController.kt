/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokemon.transformation.controller

import com.cobblemon.mod.common.api.pokemon.transformation.TransformationLike
import com.cobblemon.mod.common.api.pokemon.transformation.proxy.PreProcessor
import com.cobblemon.mod.common.api.pokemon.transformation.progress.TransformationProgress
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.controller.CobblemonClientTransformationController
import com.cobblemon.mod.common.pokemon.transformation.controller.CobblemonServerTransformationController

/**
 * Responsible for holding all available [TransformationLike]s in the [Pokemon].
 * Also handles all the networking behind them.
 * For the Cobblemon default implementations see [CobblemonClientTransformationController] & [CobblemonServerTransformationController].
 *
 * @author Licious
 * @since April 28th, 2022
 */
interface TransformationController<T : TransformationLike, out I : PreProcessor> : MutableSet<T> {

    /**
     * Resolves the [Pokemon] attached to this controller.
     *
     * @return The [Pokemon] this controller is attached to.
     */
    fun pokemon(): Pokemon

    /**
     * Starts the given transformation on this controller.
     * All the necessary networking will be handled.
     *
     * @param transformation The [TransformationLike] starting.
     */
    fun start(transformation: T)

    /** Reverts the active [TransformationLike] for this controller's [pokemon]. */
    fun revert() // TODO dunno if i like this

    /**
     * Returns an immutable collection of [TransformationProgress]es attached to this controller.
     *
     * @return The currently tracked [TransformationProgress]es.
     */
    fun progress(): Collection<TransformationProgress<*>>

    /**
     * Attaches a [TransformationProgress] to this controller.
     *
     * @param P The type of [TransformationProgress].
     * @param progress The [TransformationProgress] being attached for tracking.
     * @return The instance being tracked.
     */
    fun <P : TransformationProgress<*>> trackProgress(progress: P): P

    /**
     * Attempts to find an [TransformationProgress] in [progress] or creates and tracks using [trackProgress].
     *
     * @param P The type of [TransformationProgress].
     * @param predicate The lambda attempting to find specific progress. This needs to ensure the [TransformationProgress] is of type [P] or the cast will fail.
     * @param progressFactory The lambda creating a new instance of type [P] if [predicate] can't find an element.
     * @return The found match in [progress] or the created and now tracked with [trackProgress].
     */
    fun <P : TransformationProgress<*>> progressFirstOrCreate(predicate: (progress: TransformationProgress<*>) -> Boolean, progressFactory: () -> P): P

    fun asIntermediate(): I

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