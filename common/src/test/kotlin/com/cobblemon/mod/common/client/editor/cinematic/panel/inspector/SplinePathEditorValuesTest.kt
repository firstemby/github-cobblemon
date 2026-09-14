package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SplinePathEditorValuesTest {
    @Test
    fun `nonfinite camera points never create or change the path`() {
        val data = JsonObject()
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE)) {
            SplinePathEditor.appendControlPoint(data, Vec3(1.0, value, 2.0))
            assertEquals(JsonObject(), data)
        }
    }

    @Test
    fun `extension rejects malformed and overflowing coordinates without changing points`() {
        val data = JsonObject()
        SplinePathEditor.appendControlPoint(data, Vec3.ZERO)
        SplinePathEditor.appendControlPoint(data, Vec3(Float.MAX_VALUE.toDouble(), 0.0, 0.0))
        val points = data.getAsJsonArray("controlPoints")
        val original = points.deepCopy()
        assertNull(SplinePathEditor.extendedPoint(points))
        assertEquals(original, points)
        points[1].asJsonObject.addProperty("x", "v.arena_center_x")
        assertNull(SplinePathEditor.extendedPoint(points))
        assertNull(SplinePathEditor.extendedPoint(JsonArray().apply { add(1); add(2) }))
    }

    @Test
    fun `extension keeps double precision`() {
        val data = JsonObject()
        val previous = Vec3(1.123456789, 2.0, 3.0)
        val last = Vec3(2.987654321, 4.0, 5.0)
        SplinePathEditor.appendControlPoint(data, previous)
        SplinePathEditor.appendControlPoint(data, last)
        assertEquals(last.scale(2.0).subtract(previous), SplinePathEditor.extendedPoint(data.getAsJsonArray("controlPoints")))
    }
}
