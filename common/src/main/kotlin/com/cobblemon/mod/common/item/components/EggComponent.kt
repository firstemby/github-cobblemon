package com.cobblemon.mod.common.item.components

import com.cobblemon.mod.common.api.pokemon.breeding.Egg
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation

/**
 * A component that stores the [Egg] data.
 */
class EggComponent(val egg: Egg?) {

    companion object {
        // Codec for saving/loading the component (serialization)
        val CODEC: Codec<EggComponent> = RecordCodecBuilder.create { builder ->
            builder.group(
                    Egg.CODEC.fieldOf("egg").forGetter { it.egg }
            ).apply(builder, ::EggComponent)
        }

        // StreamCodec for network synchronization
        val PACKET_CODEC: StreamCodec<ByteBuf, EggComponent> = ByteBufCodecs.fromCodec(CODEC)
    }

    override fun equals(other: Any?): Boolean {
        return other is EggComponent && this.egg == other.egg
    }

    override fun hashCode(): Int {
        return egg.hashCode()
    }
}
