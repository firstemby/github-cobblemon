package com.cobblemon.mod.common.client.gui.stadium.widget

import com.cobblemon.mod.common.battles.stadium.StadiumConfiguration
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import net.minecraft.client.Minecraft
import net.minecraft.client.sounds.SoundManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StadiumFormatLibraryTest {
    @Test fun `only drawn format rows can be selected`() {
        mockkStatic(Minecraft::class)
        val client = mockk<Minecraft>(relaxed = true)
        every { Minecraft.getInstance() } returns client
        every { client.soundManager } returns mockk<SoundManager>(relaxed = true)
        try {
            val selected = mutableListOf<String>()
            val library = StadiumFormatLibrary(0, 0, 80, 75, linkedMapOf(
                "one" to StadiumConfiguration.template(false),
                "two" to StadiumConfiguration.template(false),
                "three" to StadiumConfiguration.template(false)
            ), "one") { selected += it }

            library.mouseClicked(1.0, 34.0, 0)
            library.mouseClicked(1.0, 74.0, 0)
            assertTrue(selected.isEmpty())

            library.mouseClicked(1.0, 37.0, 0)

            assertEquals(listOf("two"), selected)
        } finally {
            unmockkStatic(Minecraft::class)
        }
    }
}
