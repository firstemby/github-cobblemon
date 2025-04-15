package com.cobblemon.mod.common.api.habitats

import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation

data class Habitat(
    val structure: ResourceLocation,
    val spawnPool: List<ResourceLocation>
) {
    companion object {
        val CODEC: Codec<Habitat> = RecordCodecBuilder.create { instance ->
            instance.group(
                ResourceLocation.CODEC.fieldOf("structure").forGetter { it.structure },
                ResourceLocation.CODEC.listOf().fieldOf("spawnPool").forGetter { it.spawnPool }
            ).apply(instance, ::Habitat)
        }

        val STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC)

        val BLANK_HABITAT = Habitat(
            structure = cobblemonResource("blank"),
            spawnPool = emptyList()
        )

    }
}