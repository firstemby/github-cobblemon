/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.controller

import com.cobblemon.mod.common.api.pokemon.transformation.Transformation
import com.cobblemon.mod.common.api.pokemon.transformation.TransformationDisplay
import com.cobblemon.mod.common.api.pokemon.transformation.controller.TransformationController
import com.cobblemon.mod.common.api.pokemon.transformation.progress.TransformationProgress
import com.cobblemon.mod.common.net.messages.client.PokemonUpdatePacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.isBusy

/**
 * Default implementation of the [TransformationController] for server [Transformation] management.
 *
 * @param pokemon The [Pokemon] attached to this controller
 * @param transformationIds
 * @param progress
 *
 * @author Licious, Segfault Guy
 * @since January 19, 2025
 */
abstract class ServerTransformationController<T : Transformation, I>(
    private val pokemon: Pokemon,
    transformationIds: Set<String>,
    progress: Set<TransformationProgress<*>>,
) : TransformationController<T> {

    abstract fun asIntermediate(): I

    protected val transformations = hashSetOf<T>()
    protected val progress = progress.toMutableSet()
    protected val transformationIds = transformationIds.toMutableSet()

    init {
        this.progress.removeIf { !it.shouldKeep(this.pokemon) }
    }

    /** Creates a [PokemonUpdatePacket] for adding a [TransformationDisplay] to this [pokemon]'s attached client [TransformationController]. */
    protected abstract fun addPacket(transformation: T): PokemonUpdatePacket<*>

    /** Creates a [PokemonUpdatePacket] for removing a [TransformationDisplay] from this [pokemon]'s attached client [TransformationController]. */
    protected abstract fun removePacket(transformation: T): PokemonUpdatePacket<*>

    /** Creates a [PokemonUpdatePacket] for clearing all [TransformationDisplay]s from this [pokemon]'s attached client [TransformationController]. */
    protected abstract fun clearPacket(): PokemonUpdatePacket<*>

    /**
     * Validates if the provided transformation is a duplicate of an existing entry in the controller. Duplication is not
     * determined by equivalency, but whether the result of the transformation is the same.
     *
     * @param element A valid [Transformation] to this controller.
     * @return Whether the result of [element] matches the result of an existing transformation belonging to this controller.
     */
    abstract fun isDuplicate(element: T): Boolean

    /**
     * Silently adds the transformation to the controller without syncing with the client counterpart. This is used
     * during data loading to ensure the server doesn't send update packets before the client is ready.
     *
     * @param element A [Transformation] to add to this controller.
     * @return Whether [element] was added to the controller.
     */
    fun silentAdd(element: T) = !this.isDuplicate(element) && this.transformations.add(element)

    /** Reevaluates the existing transformations of this controller and removes any that no longer satisfy [Transformation.test] for [pokemon]. */
    fun reevaluate() = this.forEach { if (!it.reevaluate(this.pokemon)) this.remove(it) }

    // FROM TransformationController ----------------------------------------------------------------------------------

    override fun canStart(transformation: T): Boolean =
        this.canStart() &&
        this.any { it === transformation }

    override fun canStart(): Boolean =
        !this.pokemon.isBusy() &&
        this.pokemon.getOwnerPlayer()?.isBusy() == false &&
        this.isNotEmpty()

    open fun start(transformation: T) { if (this.canStart(transformation)) transformation.forceStart(this.pokemon) }

    open fun progress(): Collection<TransformationProgress<*>> = this.progress.toList()

    open fun <P : TransformationProgress<*>> trackProgress(progress: P): P {
        this.progress.add(progress)
        return progress
    }

    /**
     * Attempts to find an [TransformationProgress] in [progress] or creates and tracks using [trackProgress].
     *
     * @param P The type of [TransformationProgress].
     * @param predicate The lambda attempting to find specific progress. This needs to ensure the [TransformationProgress] is of type [P] or the cast will fail.
     * @param progressFactory The lambda creating a new instance of type [P] if [predicate] can't find an element.
     * @return The found match in [progress] or the created and now tracked with [trackProgress].
     */
    open fun <P : TransformationProgress<*>> progressFirstOrCreate(predicate: (progress: TransformationProgress<*>) -> Boolean, progressFactory: () -> P): P {
        val existing = this.progress.firstOrNull(predicate)
        if (existing == null) {
            val created = progressFactory()
            this.progress.add(created)
            return created
        }
        return existing as P
    }

    // FROM MutableSet ------------------------------------------------------------------------------------------------

    override val size: Int get() = this.transformations.size

    override fun add(element: T): Boolean {
        /**
         * Prevents duplicate transformations that result in the same outcome. For example, Karrablast can evolve via
         * trade or item interact, which otherwise creates duplicate entries in the summary screen and wastes an item
         * if the evolution was already unlocked.
         */
        if (!this.isDuplicate(element) && this.transformations.add(element)) {
            this.pokemon.notify(addPacket(element))
            return true
        }
        return false
    }

    override fun addAll(elements: Collection<T>): Boolean {
        var result = false
        elements.forEach { element ->
            if (this.add(element)) {
                result = true
            }
        }
        return result
    }

    override fun clear() {
        // We don't want to send unnecessary packets
        if (this.transformations.isNotEmpty()) {
            this.transformations.clear()
            this.pokemon.notify(clearPacket())
        }
        this.progress.clear()
    }

    override fun contains(element: T) = this.transformations.contains(element)

    override fun containsAll(elements: Collection<T>) = this.transformations.containsAll(elements)

    override fun isEmpty() = this.transformations.isEmpty()

    override fun iterator() = this.transformations.iterator()

    override fun remove(element: T): Boolean {
        if (this.transformations.remove(element)) {
            this.pokemon.notify(removePacket(element))
            return true
        }
        return false
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        var result = false
        elements.forEach { element ->
            if (this.remove(element)) {
                result = true
            }
        }
        return result
    }

    override fun retainAll(elements: Collection<T>): Boolean  {
        var result = false
        val comparedSet = elements.toSet()
        val iterator = this.iterator()
        while (iterator.hasNext()) {
            val element = iterator.next()
            if (!comparedSet.contains(element)) {
                iterator.remove()
                result = true
            }
        }
        return result
    }
}