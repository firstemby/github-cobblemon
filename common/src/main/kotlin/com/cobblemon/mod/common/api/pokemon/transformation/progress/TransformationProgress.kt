/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokemon.transformation.progress

import com.cobblemon.mod.common.api.data.Identifiable
import com.cobblemon.mod.common.api.pokemon.transformation.evolution.EvolutionController
import com.cobblemon.mod.common.api.pokemon.transformation.requirement.TransformationRequirement
import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * Represents a container of progression towards the completion of a [TransformationRequirement].
 * To register a transformation progress use [TransformationProgressTypes.registerType].
 *
 * @param T The type of the progression.
 *
 * @author Licious
 * @since January 27th, 2023
 */
interface TransformationProgress<T> : Identifiable {

    /**
     * Gets the current progress of type [T].
     *
     * @return The progress of type [T].
     */
    fun currentProgress(): T

    /**
     * Updates the current progress with the given [progress].
     *
     * @param progress The new state of the progress of type [T].
     */
    fun updateProgress(progress: T)

    /**
     * Resets the current progress.
     * This will create the updated instance and invoke [updateProgress].
     */
    fun reset()

    /**
     * Used to decide if the given progress should be kept in the [pokemon]s [EvolutionController].
     *
     * @param pokemon The [Pokemon] being queried.
     * @return True if the progress is still required otherwise false.
     */
    fun shouldKeep(pokemon: Pokemon): Boolean

    /**
     * The [TransformationProgressType] of this.
     *
     * @return The [TransformationProgressType] of this.
     */
    fun type(): TransformationProgressType<*>

    companion object {

        @JvmStatic
        fun codec() = TransformationProgressTypes.codec()
    }

}