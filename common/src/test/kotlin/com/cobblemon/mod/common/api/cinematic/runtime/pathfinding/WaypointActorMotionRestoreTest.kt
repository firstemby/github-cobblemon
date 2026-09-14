package com.cobblemon.mod.common.api.cinematic.runtime.pathfinding

import com.cobblemon.mod.common.junit.BootstrapMinecraft
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.minecraft.world.entity.Mob
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Test

@BootstrapMinecraft
class WaypointActorMotionRestoreTest {
    @Test fun `navigator failure does not skip original AI gravity or velocity`() {
        val entity = mockk<Mob>(relaxed = true)
        val playback = mockk<WaypointPlayback>()
        every { playback.close() } throws IllegalStateException("navigator failed")
        val velocity = Vec3(1.0, 2.0, 3.0)
        val motion = restoreFixture(entity, playback, velocity)

        motion.restore()

        verify { entity.isNoAi = false }
        verify { entity.isNoGravity = true }
        verify { entity.deltaMovement = velocity }
    }

    @Test fun `failed AI restore does not skip gravity or velocity`() {
        val entity = mockk<Mob>(relaxed = true)
        val playback = mockk<WaypointPlayback>(relaxed = true)
        every { entity.isNoAi = false } throws IllegalStateException("AI restore failed")
        val velocity = Vec3(1.0, 2.0, 3.0)
        val motion = restoreFixture(entity, playback, velocity)

        motion.restore()

        verify { playback.close() }
        verify { entity.isNoGravity = true }
        verify { entity.deltaMovement = velocity }
    }

    private fun restoreFixture(entity: Mob, playback: WaypointPlayback, velocity: Vec3): WaypointActorMotion {
        val motion = mockk<WaypointActorMotion>()
        every { motion.restore() } answers { callOriginal() }
        mapOf("entity" to entity, "playback" to playback, "originalAi" to false,
            "originalGravity" to true, "originalVelocity" to velocity).forEach { (name, value) ->
            WaypointActorMotion::class.java.getDeclaredField(name).apply { isAccessible = true }.set(motion, value)
        }
        return motion
    }
}
