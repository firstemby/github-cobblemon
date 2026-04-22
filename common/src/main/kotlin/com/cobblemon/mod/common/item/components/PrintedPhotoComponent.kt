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
    val mapId: Int,
    val createdAt: Long
) {
    companion object {
        private val UUID_CODEC: Codec<UUID> = Codec.STRING.xmap(UUID::fromString, UUID::toString)

        val CODEC: Codec<PrintedPhotoComponent> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("photo_id").forGetter { it.photoId },
                UUID_CODEC.fieldOf("owner").forGetter { it.owner },
                Codec.LONG.fieldOf("index").forGetter { it.index },
                Codec.INT.fieldOf("map_id").forGetter { it.mapId },
                Codec.LONG.fieldOf("created_at").forGetter { it.createdAt }
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

