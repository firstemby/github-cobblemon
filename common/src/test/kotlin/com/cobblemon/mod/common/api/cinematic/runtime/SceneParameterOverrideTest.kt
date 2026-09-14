package com.cobblemon.mod.common.api.cinematic.runtime

import com.cobblemon.mod.common.api.camera.model.ParameterDefault
import com.cobblemon.mod.common.api.camera.model.ParameterDefinition
import com.cobblemon.mod.common.api.camera.model.ParameterType
import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.Scene
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class SceneParameterOverrideTest {
    @Test fun `nonfinite overrides retain complete parameter defaults`() {
        val scene = Scene("parameters", startBeatId = "main", beats = mapOf("main" to Beat("main")), parameters = mapOf(
            "number" to ParameterDefinition(ParameterType.NUMBER, default = ParameterDefault.NumberDefault(3.0)),
            "point" to ParameterDefinition(ParameterType.VEC3, default = ParameterDefault.Vec3Default(1.0, 2.0, 3.0))
        ))
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            for (point in listOf(Vec3(invalid, 4.0, 5.0), Vec3(4.0, invalid, 5.0), Vec3(4.0, 5.0, invalid))) {
                val instance = SceneInstance(UUID.randomUUID(), scene, Vec3.ZERO, emptyMap(),
                    parameterOverrides = mapOf("number" to invalid, "point" to point))
                try {
                    assertTrue(instance.start())
                    assertEquals(3.0, instance.getVariable("number"))
                    assertEquals(1.0, instance.getVariable("point_x"))
                    assertEquals(2.0, instance.getVariable("point_y"))
                    assertEquals(3.0, instance.getVariable("point_z"))
                } finally { instance.stop() }
            }
        }
    }
}
