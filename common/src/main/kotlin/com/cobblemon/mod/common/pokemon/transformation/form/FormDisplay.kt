package com.cobblemon.mod.common.pokemon.transformation.form

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.transformation.TransformationDisplay
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.readIdentifier
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.writeIdentifier
import com.cobblemon.mod.common.util.writeString
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * Represents a changeable form of a [Pokemon], this is the client side counterpart of [TemporaryForm].
 *
 * @author Segfault Guy
 * @since January 19th, 2025
 */
data class FormDisplay(
    override val id: String,
    override val species: Species,
    override val aspects: Set<String>
) : TransformationDisplay {

    constructor(id: String, pokemon: Pokemon) : this(id, pokemon.species, pokemon.aspects)

    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeString(this.id)
        buffer.writeIdentifier(this.species.resourceIdentifier)
        buffer.writeCollection(this.aspects) { pb, value -> pb.writeString(value) }
    }

    companion object {
        @JvmStatic
        val CODEC: Codec<FormDisplay> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("id").forGetter(FormDisplay::id),
                Species.BY_IDENTIFIER_CODEC.fieldOf("species").forGetter(FormDisplay::species),
                Codec.list(Codec.STRING).fieldOf("aspects").forGetter { it.aspects.toList() }
            ).apply(instance) { id, species, aspects -> FormDisplay(id, species, aspects.toSet()) }
        }

        fun decode(buffer: RegistryFriendlyByteBuf): FormDisplay {
            val id = buffer.readString()
            val speciesIdentifier = buffer.readIdentifier()
            val species = PokemonSpecies.getByIdentifier(speciesIdentifier)
                ?: throw IllegalArgumentException("Cannot resolve species from $speciesIdentifier")
            val aspects = buffer.readList(ByteBuf::readString).toSet()
            return FormDisplay(id, species, aspects)
        }
    }
}