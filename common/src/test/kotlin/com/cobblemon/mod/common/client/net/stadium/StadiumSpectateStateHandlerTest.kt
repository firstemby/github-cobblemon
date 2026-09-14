package com.cobblemon.mod.common.client.net.stadium

import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.net.messages.client.stadium.StadiumSpectateStatePacket
import com.cobblemon.mod.common.net.messages.server.stadium.StadiumSpectatePacket
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

@BootstrapMinecraft
class StadiumSpectateStateHandlerTest {
    private lateinit var client: Minecraft
    private val sent = mutableListOf<StadiumSpectatePacket>()

    @BeforeEach fun setup() {
        mockkStatic(Minecraft::class, CobblemonNetwork::class)
        client = mockk(relaxed = true)
        client.player = mockk<LocalPlayer>(relaxed = true).also { every { it.uuid } returns UUID.randomUUID() }
        client.level = mockk(relaxed = true)
        every { client.connection } returns mockk(relaxed = true)
        every { Minecraft.getInstance() } returns client
        every { CobblemonNetwork.sendToServer(any()) } answers {
            (firstArg<Any>() as? StadiumSpectatePacket)?.let(sent::add)
            Unit
        }
        StadiumSpectateStateHandler.reset()
    }

    @AfterEach fun cleanup() {
        StadiumSpectateStateHandler.reset()
        unmockkStatic(Minecraft::class, CobblemonNetwork::class)
    }

    private fun available(id: UUID = UUID.randomUUID()) = id.also {
        StadiumSpectateStateHandler.handle(StadiumSpectateStatePacket(it, Component.empty()), client)
    }

    @Test fun `synchronous send failure permits the next spectator toggle`() {
        available()
        every { CobblemonNetwork.sendToServer(any()) } throws IllegalStateException("disconnected")
        StadiumSpectateStateHandler.toggle(0)
        every { CobblemonNetwork.sendToServer(any()) } answers {
            (firstArg<Any>() as? StadiumSpectatePacket)?.let(sent::add)
            Unit
        }

        StadiumSpectateStateHandler.toggle(0)

        assertEquals(1, sent.size)
    }

    @Test fun `missing acknowledgement expires pending spectator toggle`() {
        available()
        StadiumSpectateStateHandler.toggle(0)
        StadiumSpectateStateHandler.tick(client, 14_999_999_999L)
        StadiumSpectateStateHandler.toggle(14_999_999_999L)
        assertEquals(1, sent.size)

        StadiumSpectateStateHandler.tick(client, 15_000_000_000L)
        StadiumSpectateStateHandler.toggle(15_000_000_000L)

        assertEquals(2, sent.size)
    }

    @Test fun `world or connection replacement clears spectator pending ownership`() {
        for (changeConnection in listOf(false, true)) {
            StadiumSpectateStateHandler.reset()
            available()
            StadiumSpectateStateHandler.toggle(0)
            if (changeConnection) every { client.connection } returns mockk(relaxed = true)
            else client.level = mockk(relaxed = true)

            StadiumSpectateStateHandler.tick(client, 0)
            StadiumSpectateStateHandler.toggle(0)
        }

        assertEquals(4, sent.size)
    }

    @Test fun `unrelated session state cannot acknowledge pending spectator toggle`() {
        available()
        StadiumSpectateStateHandler.toggle(0)
        StadiumSpectateStateHandler.handle(StadiumSpectateStatePacket(UUID.randomUUID(), Component.empty()), client)
        StadiumSpectateStateHandler.toggle(0)

        assertEquals(1, sent.size)
    }
}
