package com.cobblemon.mod.common.pokemon.transformation.controller

import com.cobblemon.mod.common.api.pokemon.transformation.Transformation
import com.cobblemon.mod.common.api.pokemon.transformation.controller.TransformationController
import com.cobblemon.mod.common.api.pokemon.transformation.proxy.PreProcessor
import com.cobblemon.mod.common.api.pokemon.transformation.progress.TransformationProgress
import com.cobblemon.mod.common.net.messages.client.PokemonUpdatePacket
import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * Default implementation of the [TransformationController] for server [Transformation] management.
 *
 * @author Licious, Segfault Guy
 * @since January 19, 2025
 */
abstract class CobblemonServerTransformationController<T : Transformation, I : PreProcessor>(
    private val pokemon: Pokemon,
    transformationIds: Set<String>,
    progress: Set<TransformationProgress<*>>,
) : TransformationController<T, I> {

    protected val transformations = hashSetOf<T>()
    protected val progress = progress.toMutableSet()
    protected val transformationIds = transformationIds.toMutableSet()

    init {
        this.progress.removeIf { !it.shouldKeep(this.pokemon) }
    }

    abstract fun addPacket(pokemon: Pokemon, transformation: T): PokemonUpdatePacket<*>

    abstract fun removePacket(pokemon: Pokemon, transformation: T): PokemonUpdatePacket<*>

    abstract fun clearPacket(pokemon: Pokemon): PokemonUpdatePacket<*>

    override val size: Int
        get() = this.transformations.size

    override fun pokemon(): Pokemon = this.pokemon

    override fun start(transformation: T) = transformation.forceStart(this.pokemon)

    override fun progress(): Collection<TransformationProgress<*>> = this.progress.toList()

    override fun <P : TransformationProgress<*>> trackProgress(progress: P): P {
        this.progress.add(progress)
        return progress
    }

    override fun <P : TransformationProgress<*>> progressFirstOrCreate(predicate: (progress: TransformationProgress<*>) -> Boolean, progressFactory: () -> P): P {
        val existing = this.progress.firstOrNull(predicate)
        if (existing == null) {
            val created = progressFactory()
            this.progress.add(created)
            return created
        }
        return existing as P
    }

    override fun add(element: T): Boolean {
        if (this.transformations.add(element)) {
            this.pokemon.notify(addPacket(pokemon, element))
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
        val pokemon = this.pokemon
        if (this.transformations.isNotEmpty()) {
            this.transformations.clear()
            this.pokemon.notify(clearPacket(pokemon))
        }
        this.progress.clear()
    }

    override fun contains(element: T) = this.transformations.contains(element)

    override fun containsAll(elements: Collection<T>) = this.transformations.containsAll(elements)

    override fun isEmpty() = this.transformations.isEmpty()

    override fun iterator() = this.transformations.iterator()

    override fun remove(element: T): Boolean {
        if (this.transformations.remove(element)) {
            this.pokemon.notify(removePacket(pokemon, element))
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