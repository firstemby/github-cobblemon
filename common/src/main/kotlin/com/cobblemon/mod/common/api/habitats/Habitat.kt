package com.cobblemon.mod.common.api.habitats

import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation

data class Habitat(
        val structure: ResourceLocation,
        val spawnPool: List<WeightedGroup>
) {
    companion object {
        val CODEC: Codec<Habitat> = RecordCodecBuilder.create { instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("structure").forGetter { it.structure },
                    WeightedGroup.CODEC.listOf().fieldOf("spawnPool").forGetter { it.spawnPool }
            ).apply(instance, ::Habitat)
        }

        val STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC)

        val BLANK_HABITAT = Habitat(
                structure = cobblemonResource("blank"),
                spawnPool = emptyList()
        )
    }

    fun flatten(): List<ResourceLocation> {
        return spawnPool.flatMap { it.spawns.map { s -> s.species } }
    }
}

data class WeightedGroup(
        val weight: Int = 1,
        val spawns: List<WeightedSpecies>
) {
    companion object {
        val CODEC: Codec<WeightedGroup> = RecordCodecBuilder.create { instance ->
            instance.group(
                    Codec.INT.optionalFieldOf("weight", 1).forGetter { it.weight },
                    WeightedSpecies.CODEC.listOf().fieldOf("spawns").forGetter { it.spawns }
            ).apply(instance, ::WeightedGroup)
        }
    }
}

data class WeightedSpecies(
        val species: ResourceLocation,
        val weight: Int = 1
) {
    companion object {
        val CODEC: Codec<WeightedSpecies> = RecordCodecBuilder.create { instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("species").forGetter { it.species },
                    Codec.INT.optionalFieldOf("weight", 1).forGetter { it.weight }
            ).apply(instance, ::WeightedSpecies)
        }
    }
}