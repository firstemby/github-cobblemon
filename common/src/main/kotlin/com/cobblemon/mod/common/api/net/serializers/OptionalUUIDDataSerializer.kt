/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.net.serializers

import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.syncher.EntityDataSerializer
import java.util.Optional
import java.util.UUID

object OptionalUUIDDataSerializer : EntityDataSerializer<Optional<UUID>> {
    val ID = cobblemonResource("optional_uuid")

    fun write(buffer: RegistryFriendlyByteBuf, value: Optional<UUID>) {
        buffer.writeBoolean(value.isPresent)
        value.ifPresent(buffer::writeUUID)
    }

    fun read(buffer: RegistryFriendlyByteBuf): Optional<UUID> {
        return if (buffer.readBoolean()) Optional.of(buffer.readUUID()) else Optional.empty()
    }

    override fun copy(value: Optional<UUID>) = value.map { it }
    override fun codec(): StreamCodec<RegistryFriendlyByteBuf, Optional<UUID>> = StreamCodec.of(::write, ::read)
}
