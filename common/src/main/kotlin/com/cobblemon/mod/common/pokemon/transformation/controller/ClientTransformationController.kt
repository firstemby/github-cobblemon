/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.controller

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.api.pokemon.transformation.TransformationDisplay
import com.cobblemon.mod.common.api.pokemon.transformation.controller.TransformationController
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * Default implementation of the [TransformationController] for client [TransformationDisplay] management.
 *
 * @author Licious, Segfault Guy
 * @since January 19, 2025
 */
abstract class ClientTransformationController<T : TransformationDisplay>(
    protected val pokemon: Pokemon,
    transformations: Set<T>,
) : TransformationController<T> {

    protected val transformations = transformations.toMutableSet()

    abstract fun acceptPacket(pokemon: Pokemon, transformation: T): NetworkPacket<*>

    abstract fun notification()

    // FROM TransformationController ----------------------------------------------------------------------------------

    override val size: Int
        get() = this.transformations.size

    override fun canStart() = this.isNotEmpty() &&
        !CobblemonClient.isBusy() &&
        !pokemon.isBusy()

    override fun canStart(transformation: T): Boolean =
        this.canStart() &&
        this.any { it === transformation }

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