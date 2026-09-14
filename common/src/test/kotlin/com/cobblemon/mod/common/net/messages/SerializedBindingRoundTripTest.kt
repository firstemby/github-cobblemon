/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages

import io.netty.buffer.Unpooled
import net.minecraft.core.RegistryAccess
import net.minecraft.network.RegistryFriendlyByteBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SerializedBindingRoundTripTest {

    private fun newBuffer(): RegistryFriendlyByteBuf =
        RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)

    @Test
    fun `Pokemon binding encodes and decodes`() {
        val original = SerializedBinding.Pokemon(
            actorId = "hero",
            propertiesSpec = "pikachu level=5",
            aspects = listOf("shiny", "female")
        )
        val buf = newBuffer()
        try {
            SerializedBinding.encode(buf, original)
            val decoded = SerializedBinding.decode(buf)
            assertEquals(original, decoded)
        } finally {
            buf.release()
        }
    }

    @Test
    fun `Bedrock binding encodes and decodes with non-default scale`() {
        val original = SerializedBinding.Bedrock(
            actorId = "stadium_prop",
            category = "cobblemon:stadium_floor",
            aspects = listOf("worn"),
            scale = 2.5f
        )
        val buf = newBuffer()
        try {
            SerializedBinding.encode(buf, original)
            val decoded = SerializedBinding.decode(buf)
            assertEquals(original, decoded)
        } finally {
            buf.release()
        }
    }
}
