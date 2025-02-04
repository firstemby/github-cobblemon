package com.cobblemon.mod.common.pokemon.transformation.evolution

import com.cobblemon.mod.common.api.pokemon.transformation.TransformationDisplay
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * Represents an evolution of a [Pokemon], this is the client side counterpart of [Evolution].
 *
 * @author Licious
 * @since April 28th, 2022
 */
data class EvolutionDisplay(
    override val id: String,
    override val species: Species,
    override val aspects: Set<String>
) : TransformationDisplay {

    constructor(id: String, pokemon: Pokemon) : this(id, pokemon.species, pokemon.aspects)

    companion object {
        @JvmStatic
        val CODEC: Codec<EvolutionDisplay> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("id").forGetter(EvolutionDisplay::id),
                Species.BY_IDENTIFIER_CODEC.fieldOf("species").forGetter(EvolutionDisplay::species),
                Codec.list(Codec.STRING).fieldOf("aspects").forGetter { it.aspects.toList() }
            ).apply(instance) { id, species, aspects -> EvolutionDisplay(id, species, aspects.toSet()) }
        }
    }
}