package com.cobblemon.mod.common.api.cinematic.runtime

import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.BeatTransition
import com.cobblemon.mod.common.api.cinematic.model.CutEntry
import com.cobblemon.mod.common.api.cinematic.model.CutTrack
import com.cobblemon.mod.common.api.cinematic.model.FeedDeclaration
import com.cobblemon.mod.common.api.cinematic.model.FeedKind
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.api.cinematic.model.Shot
import com.cobblemon.mod.common.api.cinematic.registry.SceneBuiltinSources
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FixedRotation
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FixedFov
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.KeyframeFov
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.LookAlongPath
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.OrbitPosition
import com.cobblemon.mod.common.client.editor.model.cinematic.SceneSerializer
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorShot
import com.cobblemon.mod.common.util.asExpressionLike
import com.google.gson.JsonParser
import io.mockk.every
import io.mockk.mockk
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.AABB
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class OrbitProgressTest {
    private val orbit = OrbitPosition(Vec3.ZERO, 10f, 30f, startAngleDeg = 25f, avoidBlocks = false, rememberProgress = true)

    @Test
    fun `orbit rejects nonfinite centers and sample times`() {
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            for (center in listOf(Vec3(invalid, 0.0, 0.0), Vec3(0.0, invalid, 0.0), Vec3(0.0, 0.0, invalid))) {
                assertThrows(IllegalArgumentException::class.java) { orbit.copy(center = center) }
            }
            assertThrows(IllegalArgumentException::class.java) { orbit.evaluate(invalid.toFloat()) }
        }
    }

    @Test
    fun `finite orbit inputs remain on the circle when float angle multiplication would overflow`() {
        val fast = orbit.copy(angularVelocityDegPerSec = Float.MAX_VALUE)
        val position = fast.evaluate(Float.MAX_VALUE)
        assertTrue(position.x.isFinite() && position.y.isFinite() && position.z.isFinite())
        assertEquals(fast.radius.toDouble(), position.horizontalDistance(), 0.000001)
    }

    @Test
    fun `invalid lens values use the orbit framing fov`() {
        val bounds = AABB(-20.0, 0.0, -20.0, 20.0, 5.0, 20.0)
        val expected = orbit.evaluate(1f, bounds)
        for (fov in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(expected, orbit.evaluate(1f, bounds, fov))
        }
    }

    @Test
    fun `path direction samples the same actor framing lens as its orbit position`() {
        val source = orbit.copy(actorIds = listOf("target"))
        val direction = LookAlongPath()
        val shot = Shot("orbit", source, direction, fov = FixedFov(30f))
        val scene = scene().copy(shots = mapOf(shot.id to shot))
        val bounds = AABB(-10.0, 0.0, -10.0, 10.0, 5.0, 10.0)
        val director = mockk<ActorDirector>()
        every { director.scene } returns scene
        every { director.bindings } returns emptyMap()
        every { director.cameraBounds("target") } returns bounds
        val result = ShotEvaluator(scene).evaluate(shot.id, 1f, director, orbitTime = 7f)!!
        assertEquals(source.evaluate(7f, bounds, 30f), result.position)
        assertEquals(direction.computeRotation(result.position, source.evaluate(7f + direction.epsilon, bounds, 30f)), result.rotation)
    }
    private fun beat(id: String, vararg shots: String) = Beat(id, cuts = shots.mapIndexed { index, shot ->
        "feed$index" to CutTrack(listOf(CutEntry(0f, shot)))
    }.toMap())

    private fun scene(remember: Boolean = true): Scene {
        val source = orbit.copy(rememberProgress = remember)
        return Scene(id = "orbits", startBeatId = "main", beats = mapOf("main" to beat("main", "orbit")),
            feeds = listOf(FeedDeclaration("feed0", FeedKind.Fullscreen), FeedDeclaration("feed1", FeedKind.Fullscreen)),
            shots = listOf("orbit", "other").associateWith { Shot(it, source, FixedRotation(CameraRotation(0f, 0f))) })
    }

    private fun sample(
        router: FeedRouter,
        evaluator: ShotEvaluator,
        beat: Beat,
        time: Float,
        elapsedBeatSeconds: Double? = null
    ) = router.buildTransformPacket(
        UUID(0, 0), evaluator, CutResolver(), ActorDirector(router.scene, emptyMap()), null, beat, time,
        elapsedBeatSeconds).feeds

    @Test fun `orbit resumes across beats and other shots without counting offscreen time`() {
        val scene = scene()
        val router = FeedRouter(scene)
        val evaluator = ShotEvaluator(scene)
        sample(router, evaluator, beat("main", "orbit"), 0f)
        assertEquals(orbit.evaluate(2f), sample(router, evaluator, beat("main", "orbit"), 2f).single().position)
        sample(router, evaluator, beat("closeup", "other"), 0f)
        sample(router, evaluator, beat("closeup", "other"), 100f)
        assertEquals(orbit.evaluate(2f), sample(router, evaluator, beat("return", "orbit"), 0f).single().position)
        assertEquals(orbit.evaluate(3f), sample(router, evaluator, beat("return", "orbit"), 1f).single().position)
    }

    @Test fun `same beat cuts pause memory and duplicate feeds do not double orbit speed`() {
        val scene = scene()
        val router = FeedRouter(scene)
        val evaluator = ShotEvaluator(scene)
        sample(router, evaluator, beat("main", "orbit", "orbit"), 0f)
        val shared = sample(router, evaluator, beat("main", "orbit", "orbit"), 2f)
        assertTrue(shared.all { it.position == orbit.evaluate(2f) })
        sample(router, evaluator, beat("main", "other"), 3f)
        assertEquals(orbit.evaluate(2f), sample(router, evaluator, beat("main", "orbit"), 9f).single().position)
        assertEquals(orbit.evaluate(3f), sample(router, evaluator, beat("main", "orbit"), 10f).single().position)
    }

    @Test fun `loop reset retains angle while preview reset and new scene clear it`() {
        val scene = scene()
        val router = FeedRouter(scene)
        val evaluator = ShotEvaluator(scene)
        val beat = beat("main", "orbit")
        sample(router, evaluator, beat, 0f)
        sample(router, evaluator, beat, 4f)
        assertEquals(orbit.evaluate(4f), sample(router, evaluator, beat, 0f).single().position)
        router.resetOrbitProgress()
        assertEquals(orbit.evaluate(0f), sample(router, evaluator, beat, 0f).single().position)
        assertEquals(orbit.evaluate(0f), sample(FeedRouter(scene), ShotEvaluator(scene), beat, 0f).single().position)
    }

    @Test fun `authoritative elapsed time counts multiple wraps and excludes a same id transition`() {
        val scene = scene()
        val router = FeedRouter(scene)
        val evaluator = ShotEvaluator(scene)
        val beat = beat("main", "orbit").copy(duration = 0.03f, isLooping = true)
        sample(router, evaluator, beat, 0f)
        val afterMultipleWraps = sample(router, evaluator, beat, 0.02f, 0.5).single().position
        assertTrue(afterMultipleWraps.distanceTo(orbit.evaluate(0.5f)) < 0.000001)
        val afterSelfTransition = sample(router, evaluator, beat, 0f, 0.0).single().position
        assertTrue(afterSelfTransition.distanceTo(orbit.evaluate(0.5f)) < 0.000001)

        val preciseRouter = FeedRouter(scene)
        val preciseEvaluator = ShotEvaluator(scene)
        sample(preciseRouter, preciseEvaluator, beat, 0f)
        sample(preciseRouter, preciseEvaluator, beat, 0f, 16_777_216.0)
        val afterFloatPrecision = sample(preciseRouter, preciseEvaluator, beat, 0f, 0.05).single().position
        val reducedTime = ((16_777_216.0 + 0.05) % 12.0).toFloat()
        assertTrue(afterFloatPrecision.distanceTo(orbit.evaluate(reducedTime)) < 0.000001)
    }

    @Test fun `state machine reports finite authoritative elapsed time`() {
        val looping = Beat("main", duration = 0.03f, isLooping = true, speed = 10f)
        val loopMachine = BeatStateMachine(Scene("loop", startBeatId = looping.id, beats = mapOf(looping.id to looping)))
        assertTrue(loopMachine.start())
        val loopResult = loopMachine.advance(0.05f) as BeatStateMachine.AdvanceResult.Running
        assertEquals(0.5, loopResult.elapsedBeatSeconds, 0.0000001)
        assertEquals(0.02f, loopMachine.beatTime, 0.000001f)

        val selfTransition = BeatTransition("main", "main", "1".asExpressionLike())
        val selfMachine = BeatStateMachine(Scene("self", startBeatId = "main", beats = mapOf("main" to Beat("main")),
            transitions = listOf(selfTransition)))
        assertTrue(selfMachine.start())
        val selfResult = selfMachine.advance(0.05f) as BeatStateMachine.AdvanceResult.Running
        assertEquals(0.0, selfResult.elapsedBeatSeconds)
        assertSame(selfTransition, selfResult.transition)

        val invalidMachine = BeatStateMachine(Scene("invalid", startBeatId = "main", beats = mapOf("main" to Beat("main"))))
        assertTrue(invalidMachine.start())
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val result = invalidMachine.advance(invalid) as BeatStateMachine.AdvanceResult.Running
            assertEquals(0.0, result.elapsedBeatSeconds)
            assertEquals(0f, invalidMachine.beatTime)
            assertEquals(0f, invalidMachine.totalTime)
        }

        val fastBeat = Beat("main", duration = Float.MAX_VALUE, speed = Float.MAX_VALUE)
        val fastMachine = BeatStateMachine(Scene("fast", startBeatId = fastBeat.id,
            beats = mapOf(fastBeat.id to fastBeat)))
        assertTrue(fastMachine.start())
        repeat(3) {
            fastMachine.advance(0.5f) { _, time -> assertTrue(time.isFinite()) }
            assertTrue(fastMachine.beatTime.isFinite())
        }
    }

    @Test fun `disabled option keeps original beat timing and setting survives editor serialization`() {
        SceneBuiltinSources.register()
        val scene = scene(false)
        val router = FeedRouter(scene)
        assertEquals(orbit.evaluate(5f), sample(router, ShotEvaluator(scene), beat("main", "orbit"), 5f).single().position)
        val editor = EditorScene.createDefault()
        val position = JsonParser.parseString("""{"type":"orbit","center":{"x":0,"y":0,"z":0},"radius":10,"angularVelocity":30}""").asJsonObject
        editor.shots["orbit"] = EditorShot("orbit", position,
            JsonParser.parseString("""{"type":"fixed","yaw":0,"pitch":0}""").asJsonObject)
        for (remember in listOf(false, true)) {
            position.addProperty("rememberProgress", remember)
            val restored = SceneSerializer.toScene(editor)
            assertEquals(remember, (restored.shots.getValue("orbit").position as OrbitPosition).rememberProgress)
        }
        val json = SceneSerializer.toJson(editor)
        json.getAsJsonObject("shots").getAsJsonObject("orbit").getAsJsonObject("position").remove("rememberProgress")
        assertFalse(OrbitPosition.fromJson(json.getAsJsonObject("shots").getAsJsonObject("orbit").getAsJsonObject("position")).rememberProgress)
    }

    @Test fun `remembered position time also drives path direction but lens uses beat time`() {
        val direction = LookAlongPath()
        val shot = Shot("orbit", orbit, direction, fov = KeyframeFov(listOf(KeyframeFov.Kf(0f, 40f), KeyframeFov.Kf(10f, 80f))))
        val scene = scene().copy(shots = mapOf(shot.id to shot))
        val result = ShotEvaluator(scene).evaluate("orbit", 1f, orbitTime = 7f)!!
        assertEquals(orbit.evaluate(7f), result.position)
        assertEquals(direction.computeRotation(result.position, orbit.evaluate(7f + direction.epsilon)), result.rotation)
        assertEquals(44f, result.fov)
    }
}
