/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.codec.internal

import com.cobblemon.mod.common.api.mark.Marks
import com.cobblemon.mod.common.api.pokemon.feature.SpeciesFeatures
import com.cobblemon.mod.common.api.pokemon.feature.SynchronizedSpeciesFeatureProvider
import com.cobblemon.mod.common.api.riding.stats.RidingStat
import com.cobblemon.mod.common.pokemon.OriginalTrainerType
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.DataKeys
import com.cobblemon.mod.common.util.codec.internal.ClientPokemonP1.Companion.FEATURES
import com.cobblemon.mod.common.util.codec.internal.ClientPokemonP1.Companion.FEATURE_ID
import com.cobblemon.mod.common.util.codec.optionalFieldOfWithDefault
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

internal data class PokemonP3(
    val originalTrainerType: OriginalTrainerType,
    val originalTrainer: Optional<String>,
    val forcedAspects: Set<String>,
    val features: List<CompoundTag>,
    val heldItemVisible: Optional<Boolean>,
    val cosmeticItem: ItemStack,
    val activeMark: Optional<ResourceLocation>,
    val marks: Set<ResourceLocation>,
    val potentialMarks: Set<ResourceLocation>,
    val markings: List<Int>,
    val rideBoosts: Map<String, Float>
) : Partial<Pokemon> {

    override fun into(other: Pokemon): Pokemon {
        other.originalTrainerType = this.originalTrainerType
        this.originalTrainer.ifPresent { other.originalTrainer = it }
        other.refreshOriginalTrainer()
        other.forcedAspects = this.forcedAspects
        this.features.forEach { featureNbt ->
            val featureId = featureNbt.getString(FEATURE_ID)
            if (featureId.isEmpty()) {
                return@forEach
            }
            val speciesFeatureProviders = SpeciesFeatures.getFeaturesFor(other.species)
                .filterIsInstance<SynchronizedSpeciesFeatureProvider<*>>()
            val feature = speciesFeatureProviders.firstNotNullOfOrNull { provider -> provider(featureNbt) } ?: return@forEach
            if (
                featureNbt.contains("keys", Tag.TAG_STRING.toInt()) &&
                !featureNbt.getList("keys", Tag.TAG_STRING.toInt()).contains(StringTag.valueOf(featureId))
            ) {
                return@forEach
            }
            other.features.removeIf { it.name == feature.name }
            other.features.add(feature)
        }
        this.heldItemVisible.ifPresent { other.heldItemVisible = it }
        this.cosmeticItem.let { other.cosmeticItem = it }
        this.activeMark.ifPresent { other.activeMark = Marks.getByIdentifier(it) }
        other.marks.clear()
        other.marks += this.marks.map { Marks.getByIdentifier(it) }.filterNotNull().toMutableSet()
        other.potentialMarks.clear()
        other.potentialMarks += this.potentialMarks.map { Marks.getByIdentifier(it) }.filterNotNull().toMutableSet()
        other.markings = this.markings
        this.rideBoosts.let { other.setRideBoosts(it.mapKeys { RidingStat.valueOf(it.key) }) }
        return other
    }

    companion object {
        internal val CODEC: MapCodec<PokemonP3> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                OriginalTrainerType.CODEC.optionalFieldOfWithDefault(DataKeys.POKEMON_ORIGINAL_TRAINER_TYPE, OriginalTrainerType.NONE).forGetter(PokemonP3::originalTrainerType),
                Codec.STRING.optionalFieldOf(DataKeys.POKEMON_ORIGINAL_TRAINER).forGetter(PokemonP3::originalTrainer),
                Codec.list(Codec.STRING).optionalFieldOf(DataKeys.POKEMON_FORCED_ASPECTS, emptyList()).forGetter { it.forcedAspects.toMutableList() },
                Codec.list(CompoundTag.CODEC).optionalFieldOf(FEATURES, emptyList()).forGetter(PokemonP3::features),
                Codec.BOOL.optionalFieldOf(DataKeys.HELD_ITEM_VISIBLE).forGetter(PokemonP3::heldItemVisible),
                ItemStack.CODEC.optionalFieldOf(DataKeys.POKEMON_COSMETIC_ITEM).forGetter { Optional.ofNullable(it.cosmeticItem.takeIf { !it.isEmpty }) },
                ResourceLocation.CODEC.optionalFieldOf(DataKeys.POKEMON_ACTIVE_MARK).forGetter(PokemonP3::activeMark),
                Codec.list(ResourceLocation.CODEC).optionalFieldOf(DataKeys.POKEMON_MARKS, emptyList()).forGetter { it.marks.toMutableList() },
                Codec.list(ResourceLocation.CODEC).optionalFieldOf(DataKeys.POKEMON_POTENTIAL_MARKS, emptyList()).forGetter { it.potentialMarks.toMutableList() },
                Codec.list(Codec.INT).optionalFieldOf(DataKeys.POKEMON_MARKINGS, listOf(0, 0, 0, 0, 0, 0)).forGetter(PokemonP3::markings),
                Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf(DataKeys.POKEMON_RIDE_BOOSTS, emptyMap<String, Float>()).forGetter(PokemonP3::rideBoosts),
            ).apply(instance) { originalTrainerType, originalTrainer, forcedAspects, features, heldItemVisible, cosmeticItem, activeMark, marks, potentialMarks, markings, rideBoosts -> PokemonP3(originalTrainerType, originalTrainer, forcedAspects.toSet(), features, heldItemVisible, cosmeticItem.orElse(ItemStack.EMPTY), activeMark, marks.toMutableSet(), potentialMarks.toMutableSet(), markings, rideBoosts) }
        }

        internal fun from(pokemon: Pokemon): PokemonP3 = PokemonP3(
            pokemon.originalTrainerType,
            Optional.ofNullable(pokemon.originalTrainer),
            pokemon.forcedAspects,
            pokemon.features.map { feature ->
                val nbt = CompoundTag()
                nbt.putString(FEATURE_ID, feature.name)
                feature.saveToNBT(nbt)
            },
            Optional.ofNullable(pokemon.heldItemVisible),
            pokemon.cosmeticItem,
            Optional.ofNullable(pokemon.activeMark?.identifier),
            pokemon.marks.map { it.identifier }.toSet(),
            pokemon.potentialMarks.map { it.identifier }.toSet(),
            pokemon.markings,
            pokemon.getRideBoosts().mapKeys { it.key.name }
        )
    }
}
