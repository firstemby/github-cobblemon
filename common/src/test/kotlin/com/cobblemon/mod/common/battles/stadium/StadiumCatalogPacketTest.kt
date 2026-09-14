/*
 * Copyright (C) 2026 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.stadium

import com.cobblemon.mod.common.net.messages.client.stadium.OpenStadiumConfigPacket
import io.netty.buffer.Unpooled
import net.minecraft.core.BlockPos
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class StadiumCatalogPacketTest {
    @Test fun `server item effects round trip without relying on client registries`() {
        val effects = mapOf(ResourceLocation.parse("cobblemon:leftovers") to "leftovers", ResourceLocation.parse("minecraft:bone") to "thickclub")
        val original = OpenStadiumConfigPacket(BlockPos(3, 64, -9), CompoundTag(), 1, 0, "doubles", effects)
        val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
        try {
            original.encode(buffer)
            val decoded = OpenStadiumConfigPacket.decode(buffer)
            assertEquals(effects, decoded.heldItems)
            assertEquals(original.blockPos, decoded.blockPos)
            assertEquals("doubles", decoded.initialLayout)
            assertEquals(1, decoded.initialTab)
            assertEquals(0, buffer.readableBytes())
        } finally {
            buffer.release()
        }
    }

    @Test fun `oversized item catalog count is rejected before allocation`() {
        val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
        try {
            buffer.writeBlockPos(BlockPos.ZERO)
            buffer.writeNbt(CompoundTag())
            buffer.writeVarInt(0)
            buffer.writeVarInt(0)
            buffer.writeUtf("singles", 16)
            buffer.writeVarInt(OpenStadiumConfigPacket.MAX_HELD_ITEMS + 1)
            assertThrows(IllegalArgumentException::class.java) { OpenStadiumConfigPacket.decode(buffer) }
        } finally {
            buffer.release()
        }
    }
}
