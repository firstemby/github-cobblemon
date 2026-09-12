/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.components

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import java.util.Optional
import java.util.UUID
import net.minecraft.core.UUIDUtil
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec

data class MegaBondComponent(val owner: UUID?, val species: String?, val bond: Int) {
    companion object {
        val CODEC: Codec<MegaBondComponent> = RecordCodecBuilder.create { instance ->
            instance.group(
                UUIDUtil.CODEC.optionalFieldOf("owner").forGetter { Optional.ofNullable(it.owner) },
                Codec.STRING.optionalFieldOf("species").forGetter { Optional.ofNullable(it.species) },
                Codec.INT.fieldOf("bond").forGetter { it.bond }
            ).apply(instance) { owner, species, bond -> MegaBondComponent(owner.orElse(null), species.orElse(null), bond) }
        }

        val PACKET_CODEC: StreamCodec<ByteBuf, MegaBondComponent> = ByteBufCodecs.fromCodec(CODEC)
    }
}
