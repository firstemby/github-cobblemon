package com.cobblemon.mod.common.api.cinematic

import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.KeyframeFov
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.KeyframePosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.KeyframeRoll
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.KeyframeRotation
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.KeyframeMovement
import com.google.gson.Gson
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class KeyframeOrderingTest {
    @Test fun `all camera channels evaluate retimed and coincident keyframes in chronological order`() {
        val authored = listOf(2f to 50f, 0f to 10f, 1f to 90f, 1f to 30f)
        val position = KeyframePosition(authored.map { (time, value) -> KeyframePosition.Kf(time, Vec3(value.toDouble(), 0.0, 0.0)) })
        val rotation = KeyframeRotation(authored.map { (time, value) -> KeyframeRotation.Kf(time, value, -value) })
        val fov = KeyframeFov(authored.map { (time, value) -> KeyframeFov.Kf(time, value) })
        val roll = KeyframeRoll(authored.map { (time, value) -> KeyframeRoll.Kf(time, value) })
        for ((time, expected) in listOf(-1f to 10f, 0f to 10f, 0.5f to 20f, 1f to 30f, 1.5f to 40f, 2f to 50f, 3f to 50f)) {
            assertEquals(expected.toDouble(), position.evaluate(time).x, "position at $time")
            assertEquals(expected, rotation.evaluate(time).yaw, "yaw at $time")
            assertEquals(-expected, rotation.evaluate(time).pitch, "pitch at $time")
            assertEquals(expected, fov.evaluate(time), "fov at $time")
            assertEquals(expected, roll.evaluate(time), "roll at $time")
        }
        val times = authored.map { it.first }
        assertEquals(times, position.keyframes.map { it.time })
        assertEquals(times, rotation.keyframes.map { it.time })
        assertEquals(times, fov.keyframes.map { it.time })
        assertEquals(times, roll.keyframes.map { it.time })
        for (source in listOf(position, rotation, fov, roll)) {
            val json = Gson().toJsonTree(source).asJsonObject
            assertFalse(json.has("playbackKeyframes"))
            assertFalse(json.has("interpolationKeyframes"))
        }
    }

    @Test fun `actor movement uses the last authored position at coincident times without reordering authoring data`() {
        val authored = listOf(2f to 20.0, 0f to 100.0, 0f to 0.0, 1f to 90.0, 1f to 10.0)
            .map { (time, x) -> KeyframeMovement.Kf(time, Vec3(x, 0.0, 0.0)) }
        val movement = KeyframeMovement(authored)
        assertEquals(2f, movement.duration)
        for ((time, expected) in listOf(-1f to 0.0, 0f to 0.0, 0.5f to 5.0, 1f to 10.0, 1.5f to 15.0, 3f to 20.0)) {
            assertEquals(expected, movement.evaluate(time).x, "movement at $time")
        }
        assertEquals(authored, movement.keyframes)
    }

    @Test fun `coincident position keyframes use the surviving destinations easing`() {
        val source = KeyframePosition(listOf(
            KeyframePosition.Kf(1f, Vec3(10.0, 0.0, 0.0), "step"),
            KeyframePosition.Kf(0f, Vec3.ZERO),
            KeyframePosition.Kf(1f, Vec3(20.0, 0.0, 0.0), "linear")
        ))
        assertEquals(Vec3(10.0, 0.0, 0.0), source.evaluate(0.5f))
    }

    @Test fun `keyframe sources reject invalid times before playback`() {
        for (time in listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { KeyframePosition(listOf(KeyframePosition.Kf(time, Vec3.ZERO))) }
            assertThrows(IllegalArgumentException::class.java) { KeyframeRotation(listOf(KeyframeRotation.Kf(time, 0f, 0f))) }
            assertThrows(IllegalArgumentException::class.java) { KeyframeFov(listOf(KeyframeFov.Kf(time, 60f))) }
            assertThrows(IllegalArgumentException::class.java) { KeyframeRoll(listOf(KeyframeRoll.Kf(time, 0f))) }
            assertThrows(IllegalArgumentException::class.java) { KeyframeMovement(listOf(KeyframeMovement.Kf(time, Vec3.ZERO))) }
        }
        assertThrows(IllegalArgumentException::class.java) { KeyframePosition(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { KeyframeRotation(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { KeyframeFov(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { KeyframeRoll(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { KeyframeMovement(emptyList()) }
    }

    @Test fun `keyframe sources reject nonfinite values before playback`() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val position = Vec3(0.0, value.toDouble(), 0.0)
            assertThrows(IllegalArgumentException::class.java) { KeyframePosition(listOf(KeyframePosition.Kf(0f, position))) }
            assertThrows(IllegalArgumentException::class.java) { KeyframeRotation(listOf(KeyframeRotation.Kf(0f, 0f, value))) }
            assertThrows(IllegalArgumentException::class.java) { KeyframeFov(listOf(KeyframeFov.Kf(0f, value))) }
            assertThrows(IllegalArgumentException::class.java) { KeyframeRoll(listOf(KeyframeRoll.Kf(0f, value))) }
            assertThrows(IllegalArgumentException::class.java) { KeyframeMovement(listOf(KeyframeMovement.Kf(0f, position))) }
        }
    }
}
