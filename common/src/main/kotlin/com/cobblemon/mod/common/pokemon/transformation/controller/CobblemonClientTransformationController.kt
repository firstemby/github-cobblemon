/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.controller

import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.api.pokemon.transformation.proxy.PreProcessor
import com.cobblemon.mod.common.api.pokemon.transformation.controller.TransformationController
import com.cobblemon.mod.common.api.pokemon.transformation.TransformationDisplay
import com.cobblemon.mod.common.api.pokemon.transformation.progress.TransformationProgress
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * Default implementation of the [TransformationController] for client [TransformationDisplay] management.
 *
 * @author Licious, Segfault Guy
 * @since January 19, 2025
 */
abstract class CobblemonClientTransformationController<T : TransformationDisplay, I : PreProcessor>(
    protected val pokemon: Pokemon,
    transformations: Set<T>,
) : TransformationController<T, I> {

    protected val transformations = transformations.toMutableSet()

    abstract fun acceptPacket(pokemon: Pokemon, transformation: T): NetworkPacket<*>

    abstract fun notification()

    // FROM TransformationController ----------------------------------------------------------------------------------

    override val size: Int
        get() = this.transformations.size

    override fun pokemon(): Pokemon = this.pokemon

    override fun canStart() = this.isNotEmpty() &&
        !CobblemonClient.isBusy() &&
        !pokemon.isBusy()

    override fun canStart(transformation: T): Boolean =
        this.canStart() &&
        this.any { it === transformation }

    override fun start(transformation: T) {
        if (this.canStart(transformation))
            CobblemonNetwork.sendToServer(acceptPacket(this.pokemon, transformation))
    }

    override fun progress(): Collection<TransformationProgress<*>> {
        // Nothing is done on the client
        return emptyList()
    }

    override fun <P : TransformationProgress<*>> trackProgress(progress: P): P {
        // Nothing is done on the client
        return progress
    }

    override fun <P : TransformationProgress<*>> progressFirstOrCreate(predicate: (progress: TransformationProgress<*>) -> Boolean, progressFactory: () -> P): P {
        // Nothing is done on the client
        return progressFactory()
    }

    // FROM MutableSet ------------------------------------------------------------------------------------------------

    override fun add(element: T): Boolean {
        val result = this.transformations.add(element)
        if (result) this.notification()
        return result
    }

    override fun addAll(elements: Collection<T>) = this.transformations.addAll(elements)

    override fun clear() {
        this.transformations.clear()
    }

    override fun iterator() = this.transformations.iterator()

    override fun remove(element: T) = this.transformations.remove(element)

    override fun removeAll(elements: Collection<T>) = this.transformations.removeAll(elements.toSet())

    override fun retainAll(elements: Collection<T>) = this.transformations.retainAll(elements.toSet())

    override fun contains(element: T) = this.transformations.contains(element)

    override fun containsAll(elements: Collection<T>) = this.transformations.containsAll(elements)

    override fun isEmpty() = this.transformations.isEmpty()
}