/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui

import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.api.molangui.container.CloseItemsPolicy
import com.cobblemon.mod.common.api.molangui.container.CollectionConfig
import com.cobblemon.mod.common.api.molangui.container.CollectionSource
import com.cobblemon.mod.common.api.molangui.container.MolangContainerMenu
import com.cobblemon.mod.common.api.molangui.container.SlotLayout
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.net.messages.client.molangui.MolangUiClosePacket
import com.cobblemon.mod.common.net.messages.client.molangui.MolangUiOpenPacket
import com.cobblemon.mod.common.net.messages.client.molangui.MolangUiUpdatePacket
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.OptionalInt
import java.util.UUID

@BootstrapMinecraft
class MolangUiServerLifecycleTest {
    private val player = mockk<ServerPlayer>(relaxed = true)
    private val packets = mutableListOf<NetworkPacket<*>>()
    private val uiId = ResourceLocation.fromNamespaceAndPath("cobblemon", "lifecycle_test")

    @BeforeEach
    fun setup() {
        val server = mockk<MinecraftServer>(relaxed = true)
        ServerPlayer::class.java.getDeclaredField("server").apply { isAccessible = true }.set(player, server)
        every { server.isSameThread } returns true
        every { player.uuid } returns UUID.randomUUID()
        mockkObject(CobblemonNetwork, MolangUiContractRegistry, MolangUiActionRegistry)
        every { CobblemonNetwork.sendPacketToPlayer(any(), any()) } answers { packets.add(secondArg()); Unit }
        every { MolangUiActionRegistry.executeOnOpen(any()) } returns Unit
        MolangUiService.closeAll()
        assertEquals(0L, MolangUiDiagnostics.snapshot().activeSessions)
    }

    @AfterEach
    fun cleanup() {
        try {
            MolangUiService.closeAll()
        } finally {
            unmockkObject(CobblemonNetwork, MolangUiContractRegistry, MolangUiActionRegistry)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["packet", "menu", "items"])
    fun `close failures still release session ownership and allow remaining sessions to close`(failure: String) {
        val first = open()
        val second = open()
        val menu = mockk<MolangContainerMenu>(relaxed = true)
        first.containerMenu = menu
        player.containerMenu = menu
        when (failure) {
            "packet" -> every { CobblemonNetwork.sendPacketToPlayer(player, any<MolangUiClosePacket>()) } throws IllegalStateException("send failed")
            "menu" -> every { player.closeContainer() } throws IllegalStateException("menu failed")
            "items" -> every { menu.releaseOwnedItemsOnce(player) } throws IllegalStateException("items failed")
        }

        MolangUiService.closeAll()
        first.close()

        assertEquals(SessionState.CLOSED, first.state)
        assertEquals(SessionState.CLOSED, second.state)
        assertNull(first.containerMenu)
        assertEquals(0, MolangUiService.activeSessionCount())
        assertEquals(0L, MolangUiDiagnostics.snapshot().activeSessions)
        verify(exactly = 1) { player.closeContainer() }
        verify(exactly = 1) { menu.releaseOwnedItemsOnce(player) }
    }

    @Test
    fun `on open self close does not send an open packet or corrupt other session counts`() {
        val existing = open()
        packets.clear()
        every { MolangUiActionRegistry.executeOnOpen(any()) } answers { firstArg<MolangUiSession>().close() }

        assertTrue(MolangUiService.open(player, MolangUiOpenRequest(uiId)) is MolangUiOpenResult.Rejected)

        assertTrue(packets.isEmpty())
        assertEquals(SessionState.ACTIVE, existing.state)
        assertEquals(1, MolangUiService.activeSessionCount())
        assertEquals(1L, MolangUiDiagnostics.snapshot().activeSessions)
        verify(exactly = 0) { player.openMenu(any()) }
    }

    @Test
    fun `closing all sessions during screen replacement cancels the new on open callback`() {
        val existing = open(contract(GuiMode.SCREEN))
        every { CobblemonNetwork.sendPacketToPlayer(player, any<MolangUiClosePacket>()) } answers { MolangUiService.closeAll() }

        assertTrue(MolangUiService.open(player, MolangUiOpenRequest(uiId)) is MolangUiOpenResult.Rejected)

        assertEquals(SessionState.CLOSED, existing.state)
        assertEquals(0, MolangUiService.activeSessionCount())
        assertEquals(0L, MolangUiDiagnostics.snapshot().activeSessions)
        verify(exactly = 1) { MolangUiActionRegistry.executeOnOpen(any()) }
        assertEquals(1, packets.filterIsInstance<MolangUiOpenPacket>().size)
    }

    @Test
    fun `opening packet failure removes only the failed session`() {
        val existing = open()
        every { CobblemonNetwork.sendPacketToPlayer(player, any<MolangUiOpenPacket>()) } throws IllegalStateException("send failed")

        assertTrue(MolangUiService.open(player, MolangUiOpenRequest(uiId)) is MolangUiOpenResult.Rejected)

        assertEquals(SessionState.ACTIVE, existing.state)
        assertEquals(1, MolangUiService.activeSessionCount())
        assertEquals(1L, MolangUiDiagnostics.snapshot().activeSessions)
    }

    @Test
    fun `invalid or failing container lookup leaves the existing screen open`() {
        val existing = open(contract(GuiMode.SCREEN))
        val collection = CollectionConfig(1, source = CollectionSource.BLOCK_ENTITY, layout = SlotLayout(0, 0, 1, 1))
        every { MolangUiContractRegistry.getContract(uiId) } returns contract(GuiMode.SCREEN, mapOf("input" to collection))
        val request = MolangUiOpenRequest(uiId, blockPos = BlockPos.ZERO)
        every { player.distanceToSqr(.5, .5, .5) } returns 65.0
        assertEquals(RejectionReason.INVALID_CONTAINER, (MolangUiService.open(player, request) as MolangUiOpenResult.Rejected).reason)
        verify(exactly = 0) { player.level() }
        every { player.distanceToSqr(.5, .5, .5) } returns 0.0
        val level = mockk<ServerLevel>(relaxed = true)
        every { player.level() } returns level
        every { level.hasChunk(0, 0) } returns false
        assertEquals(RejectionReason.INVALID_CONTAINER, (MolangUiService.open(player, request) as MolangUiOpenResult.Rejected).reason)
        verify(exactly = 0) { level.getBlockEntity(any()) }
        every { player.level() } throws IllegalStateException("level unavailable")
        assertEquals(RejectionReason.INTERNAL_ERROR, (MolangUiService.open(player, request) as MolangUiOpenResult.Rejected).reason)

        assertEquals(SessionState.ACTIVE, existing.state)
        assertEquals(1L, MolangUiDiagnostics.snapshot().activeSessions)
    }

    @Test
    fun `rejected native menu closes the client session`() {
        val collection = CollectionConfig(1, layout = SlotLayout(0, 0, 1, 1))
        every { MolangUiContractRegistry.getContract(uiId) } returns contract(GuiMode.SCREEN, mapOf("input" to collection))
        every { player.openMenu(any()) } returns OptionalInt.empty()

        assertTrue(MolangUiService.open(player, MolangUiOpenRequest(uiId)) is MolangUiOpenResult.Rejected)

        assertEquals(1, packets.filterIsInstance<MolangUiOpenPacket>().size)
        assertEquals(1, packets.filterIsInstance<MolangUiClosePacket>().size)
        assertEquals(0, MolangUiService.activeSessionCount())
        assertEquals(0L, MolangUiDiagnostics.snapshot().activeSessions)
    }

    @Test
    fun `failed parameter send leaves the server parameter unchanged`() {
        val contract = contract().copy(parameters = mapOf("value" to ParameterDefinition(ParameterType.NUMBER,
            default = ParameterDefault.Literal(ParameterValue.NumberValue(1.0)))))
        val session = open(contract)
        every { CobblemonNetwork.sendPacketToPlayer(player, any<MolangUiUpdatePacket>()) } throws IllegalStateException("send failed")

        assertThrows(IllegalStateException::class.java) { session.updateParameters(mapOf("value" to ParameterValue.NumberValue(2.0))) }

        assertEquals(ParameterValue.NumberValue(1.0), session.getParameter("value"))
    }

    private fun open(contract: MolangUiContract = contract()): MolangUiSession {
        every { MolangUiContractRegistry.getContract(uiId) } returns contract
        return (MolangUiService.open(player, MolangUiOpenRequest(uiId)) as MolangUiOpenResult.Opened).session as MolangUiSession
    }

    private fun contract(mode: GuiMode = GuiMode.OVERLAY, collections: Map<String, CollectionConfig> = emptyMap()) = MolangUiContract(
        uiId, MOLANG_UI_FORMAT_VERSION, "1", mode, emptyMap(), emptyMap(), null,
        collections, emptyList(), CloseItemsPolicy.RETURN, false
    )
}
