package com.cobblemon.mod.common.client.render.cinematic

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CinematicWorldRendererPrecisionTest {

    @Test
    fun `camera subtraction precedes float conversion`() {
        val position = cameraRelativePosition(Vec3(30_000_000.125, 64.0, 30_000_000.25), Vec3(30_000_000.0, 64.0, 30_000_000.0))

        assertEquals(0.125f, position.x)
        assertEquals(0.25f, position.z)
    }
}
