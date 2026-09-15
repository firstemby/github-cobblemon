/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui

import com.cobblemon.mod.common.api.molangui.container.SlotLayout
import com.cobblemon.mod.common.api.molangui.container.ShiftClickRule
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.net.messages.client.molangui.MolangUiOpenPacket
import com.google.gson.JsonParser
import com.google.gson.JsonObject
import io.netty.buffer.Unpooled
import net.minecraft.core.RegistryAccess
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

@BootstrapMinecraft
class SlotLayoutValidationTest {
    private val id = ResourceLocation.fromNamespaceAndPath("cobblemon", "layout_validation")

    @Test
    fun `contract rejects invalid grids and lossy integer conversions`() {
        listOf(
            "\"grid_dimensions\":[-1,-1]",
            "\"grid_dimensions\":[65537,-65535]",
            "\"grid_dimensions\":[4294967297,1]",
            "\"grid_dimensions\":[1.5,1]",
            "\"grid_dimensions\":[\"1\",1]",
            "\"grid_dimensions\":[17,1]",
            "\"slot_size\":[0,18]",
            "\"slot_size\":[18,-1]",
            "\"slot_spacing\":[-1,0]",
            "\"offset\":[2147483647,0]",
            "\"slot_size\":[2147483647,18],\"slot_spacing\":[1,0]"
        ).forEach { fields ->
            assertThrows(IllegalArgumentException::class.java, { parseContract(fields) }, fields)
        }
        assertThrows(IllegalArgumentException::class.java) { parseContract("", "4294967297") }
        assertThrows(IllegalArgumentException::class.java) { parseContract("", "1.5") }
    }

    @Test
    fun `bounded grids preserve negative offsets and custom spacing through packet round trip`() {
        val contract = parseContract(
            "\"grid_dimensions\":[16,16],\"offset\":[-32,-18],\"slot_size\":[20,22],\"slot_spacing\":[2,3]",
            "256"
        )
        val layout = contract.collections.getValue("items").layout
        val packet = MolangUiOpenPacket(UUID.randomUUID(), id, "1", GuiMode.SCREEN, emptyMap(), false, true, mapOf("items" to layout))
        val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
        try {
            packet.encode(buffer)
            val decoded = MolangUiOpenPacket.decode(buffer)
            assertEquals(SlotLayout(-32, -18, 16, 16, 20, 22, 2, 3), decoded.slotLayouts.getValue("items"))
            assertEquals(256, decoded.slotLayouts.getValue("items").slotCount)
            assertEquals(0, buffer.readableBytes())
        } finally {
            buffer.release()
        }
    }

    @Test
    fun `packet decode rejects dimensions that would overflow or create an empty grid`() {
        listOf(-1 to -1, 0 to 1, 65537 to -65535, 17 to 1).forEach { (columns, rows) ->
            val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
            try {
                buffer.writeUUID(UUID.randomUUID())
                buffer.writeUtf(id.toString())
                buffer.writeUtf("1")
                buffer.writeEnum(GuiMode.SCREEN)
                buffer.writeVarInt(0)
                buffer.writeBoolean(false).writeBoolean(true)
                buffer.writeVarInt(1)
                buffer.writeUtf("items")
                listOf(0, 0, columns, rows, 18, 18, 0, 0).forEach(buffer::writeVarInt)
                assertThrows(IllegalArgumentException::class.java) { MolangUiOpenPacket.decode(buffer) }
            } finally {
                buffer.release()
            }
        }
    }

    @Test
    fun `slot coordinate validation rejects intermediate multiplication overflow`() {
        assertThrows(IllegalArgumentException::class.java) { SlotLayout(Int.MIN_VALUE, 0, 16, 1, Int.MAX_VALUE) }
        assertThrows(IllegalArgumentException::class.java) { SlotLayout(0, Int.MIN_VALUE, 1, 16, slotHeight = Int.MAX_VALUE) }
    }

    @Test
    fun `contract and packet reject reserved collections and inconsistent container modes`() {
        val layout = SlotLayout(0, 0, 1, 1)
        for (name in ShiftClickRule.PLAYER_SIDE_NAMES) {
            val layouts = mapOf(name to layout)
            assertThrows(IllegalArgumentException::class.java) { contractWithLayouts(layouts) }
            assertThrows(IllegalArgumentException::class.java) { packet(layouts) }
        }
        val layouts = mapOf("items" to layout)
        assertThrows(IllegalArgumentException::class.java) { contractWithLayouts(layouts, GuiMode.OVERLAY) }
        assertThrows(IllegalArgumentException::class.java) { packet(layouts, GuiMode.OVERLAY) }
        assertThrows(IllegalArgumentException::class.java) {
            MolangUiOpenPacket(UUID.randomUUID(), id, "1", GuiMode.SCREEN, emptyMap(), false, false, layouts)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MolangUiOpenPacket(UUID.randomUUID(), id, "1", GuiMode.SCREEN, emptyMap(), false, true)
        }
        val collections = (0 until MolangUiPacketLimits.MAX_CONTRACTS).associate { "c$it" to layout }
        contractWithLayouts(collections)
        packet(collections)
        assertThrows(IllegalArgumentException::class.java) { contractWithLayouts(collections + ("extra" to layout)) }
        assertThrows(IllegalArgumentException::class.java) { packet(collections + ("extra" to layout)) }
    }

    @Test
    fun `native signed short slot boundary includes all player inventory slots`() {
        fun layouts(slots: Int): Map<String, SlotLayout> {
            val result = linkedMapOf<String, SlotLayout>()
            var remaining = slots
            while (remaining > 0) {
                val layout = if (remaining >= 256) SlotLayout(0, 0, 16, 16)
                    else SlotLayout(0, 0, minOf(16, remaining), 1)
                result["c${result.size}"] = layout
                remaining -= layout.slotCount
            }
            return result
        }
        val valid = layouts(32768 - 36)
        assertEquals(32768 - 36, contractWithLayouts(valid).collections.values.sumOf { it.size })
        val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
        try {
            packet(valid).encode(buffer)
            assertEquals(valid, MolangUiOpenPacket.decode(buffer).slotLayouts)
            assertEquals(0, buffer.readableBytes())
        } finally {
            buffer.release()
        }
        val overflow = layouts(32768 - 36 + 1)
        assertThrows(IllegalArgumentException::class.java) { contractWithLayouts(overflow) }
        assertThrows(IllegalArgumentException::class.java) { packet(overflow) }
    }

    @Test
    fun `packet decoder rejects duplicate keys invalid modes and trailing data`() {
        fun reject(args: List<Pair<String, String>> = emptyList(), names: List<String> = emptyList(),
                   mode: GuiMode = GuiMode.SCREEN, hasContainer: Boolean = names.isNotEmpty(), trailing: Boolean = false) {
            val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
            try {
                buffer.writeUUID(UUID.randomUUID())
                buffer.writeUtf(id.toString())
                buffer.writeUtf("1")
                buffer.writeEnum(mode)
                buffer.writeVarInt(args.size)
                args.forEach { (key, value) -> buffer.writeUtf(key).writeUtf(value) }
                buffer.writeBoolean(false).writeBoolean(hasContainer)
                if (hasContainer) {
                    buffer.writeVarInt(names.size)
                    names.forEach { name ->
                        buffer.writeUtf(name)
                        listOf(0, 0, 1, 1, 18, 18, 0, 0).forEach(buffer::writeVarInt)
                    }
                }
                if (trailing) buffer.writeByte(0)
                assertThrows(IllegalArgumentException::class.java) { MolangUiOpenPacket.decode(buffer) }
            } finally {
                buffer.release()
            }
        }
        reject(args = listOf("title" to "first", "title" to "second"))
        reject(names = listOf("items", "items"))
        ShiftClickRule.PLAYER_SIDE_NAMES.forEach { reject(names = listOf(it)) }
        reject(names = listOf("items"), mode = GuiMode.OVERLAY)
        reject(hasContainer = true)
        reject(trailing = true)
    }

    @Test
    fun `opening packet enforces byte budget for multibyte text`() {
        val args = (0 until MolangUiPacketLimits.MAX_ACTION_FIELDS).associate {
            "p$it" to "€".repeat(MolangUiPacketLimits.MAX_VALUE_LENGTH)
        }
        val packet = MolangUiOpenPacket(UUID.randomUUID(), id, "1", GuiMode.SCREEN, args, false)
        val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY)
        try {
            assertThrows(IllegalArgumentException::class.java) { packet.encode(buffer) }
        } finally {
            buffer.release()
        }
    }

    private fun packet(layouts: Map<String, SlotLayout>, mode: GuiMode = GuiMode.SCREEN) =
        MolangUiOpenPacket(UUID.randomUUID(), id, "1", mode, emptyMap(), false, layouts.isNotEmpty(), layouts)

    private fun contractWithLayouts(layouts: Map<String, SlotLayout>, mode: GuiMode = GuiMode.SCREEN): MolangUiContract {
        val json = JsonParser.parseString("""{"format_version":1,"contract_version":"1","mode":"${mode.name.lowercase()}"}""").asJsonObject
        val collections = JsonObject()
        layouts.forEach { (name, layout) ->
            collections.add(name, JsonParser.parseString("""{"size":${layout.slotCount},"layout":{
                "offset":[${layout.offsetX},${layout.offsetY}],"grid_dimensions":[${layout.columns},${layout.rows}]}}"""))
        }
        json.add("collections", collections)
        return MolangUiContractRegistry.parse(id, json)
    }

    private fun parseContract(layout: String, size: String = "1") = MolangUiContractRegistry.parse(id, JsonParser.parseString("""
        {"format_version":1,"contract_version":"1","mode":"screen","collections":{
          "items":{"size":$size,"layout":{$layout}}
        }}
    """).asJsonObject)
}
