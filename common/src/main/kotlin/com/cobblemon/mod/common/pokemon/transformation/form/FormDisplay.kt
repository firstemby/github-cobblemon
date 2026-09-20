/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.form

import com.bedrockk.molang.runtime.value.MoValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.transformation.TransformationDisplay
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.addMutableCollectionFunctions
import com.cobblemon.mod.common.util.addReferentialGetterSetter
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
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
    override var species: Species,
    override var aspects: MutableSet<String>
) : TransformationDisplay {
    @Transient
    val struct = ObjectValue<FormDisplay>(this)
        .addMutableCollectionFunctions(
            collection = aspects,
            name = "aspect",
            parser = MoValue::asString,
            serializer = ::StringValue
        )
        .addReferentialGetterSetter(
            property = FormDisplay::species,
            serializer = { value -> StringValue(value.resourceIdentifier.toString()) },
            deserializer = { value -> PokemonSpecies.getByIdentifier(value.asString().asIdentifierDefaultingNamespace()) }
        )

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
            ).apply(instance) { id, species, aspects -> FormDisplay(id, species, aspects.toMutableSet()) }
        }

        fun decode(buffer: RegistryFriendlyByteBuf): FormDisplay {
            val id = buffer.readString()
            val speciesIdentifier = buffer.readIdentifier()
            val species = PokemonSpecies.getByIdentifier(speciesIdentifier)
                ?: throw IllegalArgumentException("Cannot resolve species from $speciesIdentifier")
            val aspects = buffer.readList(ByteBuf::readString).toMutableSet()
            return FormDisplay(id, species, aspects)
        }
    }
}