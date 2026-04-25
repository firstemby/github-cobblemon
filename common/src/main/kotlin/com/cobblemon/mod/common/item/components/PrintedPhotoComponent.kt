/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.components

import com.cobblemon.mod.common.CobblemonItemComponents
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.ItemStack
import java.util.UUID

data class PrintedPhotoComponent(
    val photoId: String,
    val owner: UUID,
    val index: Long,
    val assetId: Int = 0,
    val printSize: Int = 1,
    val createdAt: Long,
    val detectedSpecies: List<String> = emptyList(),
    val biome: String = "minecraft:unknown",
    val dimension: String = "minecraft:overworld",
    val timeOfDayTicks: Long = 0L
) {
    companion object {
        private val UUID_CODEC: Codec<UUID> = Codec.STRING.xmap(UUID::fromString, UUID::toString)

        val CODEC: Codec<PrintedPhotoComponent> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("photo_id").forGetter { it.photoId },
                UUID_CODEC.fieldOf("owner").forGetter { it.owner },
                Codec.LONG.fieldOf("index").forGetter { it.index },
                Codec.INT.optionalFieldOf("asset_id", 0).forGetter { it.assetId },
                Codec.intRange(1, 3).optionalFieldOf("print_size", 1).forGetter { it.printSize },
                Codec.LONG.fieldOf("created_at").forGetter { it.createdAt },
                Codec.list(Codec.STRING).optionalFieldOf("detected_species", emptyList()).forGetter { it.detectedSpecies },
                Codec.STRING.optionalFieldOf("biome", "minecraft:unknown").forGetter { it.biome },
                Codec.STRING.optionalFieldOf("dimension", "minecraft:overworld").forGetter { it.dimension },
                Codec.LONG.optionalFieldOf("time_of_day_ticks", 0L).forGetter { it.timeOfDayTicks }
            ).apply(instance, ::PrintedPhotoComponent)
        }

        val PACKET_CODEC: StreamCodec<ByteBuf, PrintedPhotoComponent> = ByteBufCodecs.fromCodec(CODEC)

        fun get(stack: ItemStack): PrintedPhotoComponent? = stack.get(CobblemonItemComponents.PRINTED_PHOTO)

        fun set(stack: ItemStack, component: PrintedPhotoComponent): ItemStack {
            stack.set(CobblemonItemComponents.PRINTED_PHOTO, component)
            return stack
        }
    }
}

