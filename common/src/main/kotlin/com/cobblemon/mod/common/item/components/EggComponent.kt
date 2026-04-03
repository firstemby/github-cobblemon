/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.components

import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import java.util.UUID
import net.minecraft.core.UUIDUtil
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

/**
 * A component that stores the egg data.
 */
class EggComponent(
    val species: ResourceLocation, // Species of the Pokémon
    val form: String,
    val eggId: UUID,
    val parent1UUID: UUID?,
    val parent1OwnerUUID: UUID?,
    val parent2UUID: UUID?,
    val parent2OwnerUUID: UUID?,
    var timeToHatch: Float         // Time in ticks to hatch
) {
    constructor(
        offspring: Pokemon,
        parent1: Pokemon?,
        parent2: Pokemon?,
        timeToHatch: Float
    ): this(
        species = offspring.species.resourceIdentifier,
        form = offspring.form.name,
        eggId = offspring.uuid,
        parent1UUID = parent1?.uuid,
        parent1OwnerUUID = parent1?.getOwnerUUID(),
        parent2UUID = parent2?.uuid,
        parent2OwnerUUID = parent2?.getOwnerUUID(),
        timeToHatch = timeToHatch
    )

    companion object {
        // Codec for saving/loading the component (serialization)
        val CODEC: Codec<EggComponent> = RecordCodecBuilder.create { instance ->0
            instance.group(
                ResourceLocation.CODEC.fieldOf("species").forGetter { it?.species ?: cobblemonResource("unknown") },
                Codec.STRING.fieldOf("form").forGetter(EggComponent::form),
                UUIDUtil.LENIENT_CODEC.fieldOf("eggId").forGetter(EggComponent::eggId),
                UUIDUtil.LENIENT_CODEC.optionalFieldOf("parent1UUID", null).forGetter { it?.parent1UUID },
                UUIDUtil.LENIENT_CODEC.optionalFieldOf("parent1OwnerUUID", null).forGetter { it?.parent1OwnerUUID },
                UUIDUtil.LENIENT_CODEC.optionalFieldOf("parent2UUID", null).forGetter { it?.parent2UUID },
                UUIDUtil.LENIENT_CODEC.optionalFieldOf("parent2OwnerUUID", null).forGetter { it?.parent2OwnerUUID },
                Codec.FLOAT.optionalFieldOf("timeToHatch", 0F).forGetter { it?.timeToHatch ?: 0F }
            ).apply(instance, ::EggComponent)
        }

        // StreamCodec for network synchronization
        val PACKET_CODEC: StreamCodec<ByteBuf, EggComponent> = ByteBufCodecs.fromCodec(CODEC)
    }

    val isWild = parent1OwnerUUID == null && parent2OwnerUUID == null
    fun isParent(pokemonId: UUID) = parent1OwnerUUID == pokemonId || parent2OwnerUUID == pokemonId

    fun createEggItemStack(): ItemStack {
        // Create a new ItemStack using the Pokémon Egg item
        val eggItemStack = CobblemonItems.POKEMON_EGG.defaultInstance

        // Add the EggData component to the ItemStack
        val eggComponent = this
        eggItemStack.set(CobblemonItemComponents.EGG_DATA, eggComponent)

        return eggItemStack
    }

    override fun equals(other: Any?) = other is EggComponent && this.eggId == other.eggId
    override fun hashCode() = eggId.hashCode()
}
