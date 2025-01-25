/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokemon.breeding

import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.block.entity.NestBlockEntity
import com.cobblemon.mod.common.item.components.EggComponent
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.DataKeys
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.codec.CodecUtils
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import java.util.*

data class Egg(
    val species: ResourceLocation, // Species of the Pokémon
    val parentOne: Pokemon?,       // First parent's UUID
    val parentTwo: Pokemon?,       // Second parent's UUID
    val form: String,              // Form of the Pokémon
    var timeToHatch: Float         // Time in ticks to hatch
) {

    companion object {
        val CODEC: Codec<Egg?> = RecordCodecBuilder.create { instance ->
            instance.group(
                ResourceLocation.CODEC.optionalFieldOf("species", cobblemonResource("unknown")).forGetter { it?.species ?: cobblemonResource("unknown") },
                Pokemon.CODEC.optionalFieldOf("parentOne").forGetter { it?.parentOne?.let { Optional.of(it) } ?: Optional.empty() },
                Pokemon.CODEC.optionalFieldOf("parentTwo").forGetter { it?.parentTwo?.let { Optional.of(it) } ?: Optional.empty() },
                Codec.STRING.optionalFieldOf("form", "").forGetter { it?.form ?: "" },
                Codec.FLOAT.optionalFieldOf("timeToHatch", 0F).forGetter { it?.timeToHatch ?: 0F }
            ).apply(instance) { species, parentOneOpt, parentTwoOpt, form, timeToHatch ->
                if (species != cobblemonResource("unknown") || timeToHatch != 0F || parentOneOpt.isPresent || parentTwoOpt.isPresent || form.isNotEmpty()) {
                    Egg(
                        species = species,
                        parentOne = parentOneOpt.orElse(null),
                        parentTwo = parentTwoOpt.orElse(null),
                        form = form,
                        timeToHatch = timeToHatch
                    )
                } else {
                    null // Represents no egg.
                }
            }
        }
    }

    fun createEggItemStack(): ItemStack {
        // Create a new ItemStack using the Pokémon Egg item
        val eggItemStack = CobblemonItems.POKEMON_EGG.defaultInstance

        // Add the EggData component to the ItemStack
        val eggComponent = EggComponent(this)
        eggItemStack.set(CobblemonItemComponents.EGG_DATA, eggComponent)

        return eggItemStack
    }

}

    /*fun getPattern(): EggPattern? {
        return EggPatterns.patternMap[patternId]
    }

    companion object {
        fun fromBlockNbt(blockNbt: CompoundTag): Egg {
            val eggNbt = blockNbt.get(DataKeys.EGG) as CompoundTag
            return fromNbt(eggNbt)
        }
        fun fromNbt(nbt: CompoundTag): Egg {
            return Egg(
                EggPokemon.fromNBT(nbt.get(DataKeys.HATCHED_POKEMON) as CompoundTag),
                ResourceLocation.tryParse(nbt.getString(DataKeys.EGG_PATTERN))!!,
                nbt.getString(DataKeys.PRIMARY_COLOR),
                if (nbt.contains(DataKeys.SECONDARY_COLOR)) nbt.getString(DataKeys.SECONDARY_COLOR) else null,
                nbt.getInt(DataKeys.TIME_TO_HATCH)
            )
        }

        //Less expensive than deserializing the whole thing, used for the color provider since it gets
        //called every frame
        fun getColorsFromNbt(nbt: CompoundTag): Pair<String, String> {
            return Pair(
                nbt.getString(DataKeys.PRIMARY_COLOR),
                nbt.getString(DataKeys.SECONDARY_COLOR)
            )
        }
    }
}*/