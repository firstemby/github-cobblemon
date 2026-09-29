/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model

import net.minecraft.network.chat.Component
import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.MoValue
import com.cobblemon.mod.common.api.cinematic.model.sources.PositionSource
import com.cobblemon.mod.common.api.cinematic.model.sources.RotationSource
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.camera.result.ValidationError
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SceneValidationTest {

    @Test fun `ending transitions neither make beats reachable nor form playback cycles`() {
        val scene = Scene("ending", startBeatId = "a", beats = mapOf("a" to Beat("a"), "b" to Beat("b")),
            transitions = listOf(BeatTransition("a", "b", StubExpression, endsScene = true),
                BeatTransition("b", "a", StubExpression)))
        val errors = scene.validate()
        assertEquals(listOf("beats[b]"), errors.map { it.path })
        assertEquals(ValidationError.Severity.WARNING, errors.single().severity)
    }

    @Test fun `long beat chains validate without recursive stack growth`() {
        val ids = (0 until 10_000).map { "beat_$it" }
        val scene = Scene("long", startBeatId = ids.first(), beats = ids.associateWith { Beat(it) },
            transitions = ids.zipWithNext().map { (from, to) -> BeatTransition(from, to, StubExpression) })
        assertTrue(scene.validate().isEmpty())
    }

    private fun minimalBeat(id: String = "main") = Beat(id = id)

    private fun minimalScene(
        startBeatId: String = "main",
        beats: Map<String, Beat> = mapOf("main" to minimalBeat())
    ) = Scene(
        id = "test",
        startBeatId = startBeatId,
        beats = beats
    )

    @Test
    fun `minimal scene with start beat validates clean`() {
        val errors = minimalScene().validate()
        assertEquals(emptyList<ValidationError>(), errors)
    }

    @Test
    fun `missing start beat is an ERROR`() {
        val scene = Scene(
            id = "test",
            startBeatId = "does_not_exist",
            beats = mapOf("other" to minimalBeat("other"))
        )
        val errors = scene.validate()
        assertTrue(errors.any { it.path == "startBeatId" && it.severity == ValidationError.Severity.ERROR })
    }

    @Test
    fun `actor map-key mismatch is reported`() {
        val scene = Scene(
            id = "test",
            startBeatId = "main",
            beats = mapOf("main" to minimalBeat()),
            actors = mapOf("hero" to ActorDeclaration(id = "rival", kind = ActorKind.PlayerTakeover))
        )
        val errors = scene.validate()
        assertTrue(errors.any { it.path == "actors[hero].id" })
    }

    @Test
    fun `shot map-key mismatch is reported`() {
        val shot = Shot(
            id = "actual_shot",
            position = StubPositionSource,
            rotation = StubRotationSource
        )
        val scene = Scene(
            id = "test",
            startBeatId = "main",
            beats = mapOf("main" to minimalBeat()),
            shots = mapOf("key_shot" to shot)
        )
        val errors = scene.validate()
        assertTrue(errors.any { it.path == "shots[key_shot].id" })
    }

    @Test
    fun `beat map-key mismatch is reported`() {
        val scene = Scene(
            id = "test",
            startBeatId = "key",
            beats = mapOf("key" to minimalBeat(id = "other"))
        )
        val errors = scene.validate()
        assertTrue(errors.any { it.path == "beats[key].id" })
    }

    @Test
    fun `duplicate feed id is reported`() {
        val scene = Scene(
            id = "test",
            startBeatId = "main",
            beats = mapOf("main" to minimalBeat()),
            feeds = listOf(
                FeedDeclaration("primary", FeedKind.Fullscreen),
                FeedDeclaration("primary", FeedKind.Pip(FeedKind.Corner.TOP_RIGHT, 0.3f))
            )
        )
        val errors = scene.validate()
        assertTrue(errors.any { it.path == "feeds" && it.message == Component.translatable("cobblemon.cinematic_editor.validation.duplicate_feed", "primary") })
    }

    @Test
    fun `actorDirection referencing undeclared actor is reported`() {
        val beat = Beat(
            id = "main",
            actorDirections = mapOf("ghost" to ActorDirection(visible = true))
        )
        val scene = Scene(
            id = "test",
            startBeatId = "main",
            beats = mapOf("main" to beat)
        )
        val errors = scene.validate()
        assertTrue(errors.any { it.path == "beats[main].actorDirections[ghost]" })
    }

    @Test
    fun `cut referencing undeclared feed is reported`() {
        val shot = Shot(id = "s", position = StubPositionSource, rotation = StubRotationSource)
        val beat = Beat(
            id = "main",
            cuts = mapOf("nonexistent_feed" to CutTrack(listOf(CutEntry(0f, "s"))))
        )
        val scene = Scene(
            id = "test",
            startBeatId = "main",
            beats = mapOf("main" to beat),
            shots = mapOf("s" to shot)
        )
        val errors = scene.validate()
        assertTrue(errors.any { it.path == "beats[main].cuts[nonexistent_feed]" })
    }

    @Test
    fun `cut referencing undeclared shot is reported`() {
        val beat = Beat(
            id = "main",
            cuts = mapOf("primary" to CutTrack(listOf(CutEntry(0f, "missing_shot"))))
        )
        val scene = Scene(
            id = "test",
            startBeatId = "main",
            beats = mapOf("main" to beat),
            feeds = listOf(FeedDeclaration("primary", FeedKind.Fullscreen))
        )
        val errors = scene.validate()
        assertTrue(errors.any {
            it.path == "beats[main].cuts[primary]" &&
                it.message == Component.translatable("cobblemon.cinematic_editor.validation.shot_missing", "missing_shot", 0f)
        })
    }

    @Test
    fun `transition referencing missing source beat is reported`() {
        val scene = Scene(
            id = "test",
            startBeatId = "main",
            beats = mapOf("main" to minimalBeat()),
            transitions = listOf(BeatTransition(
                sourceBeatId = "ghost",
                targetBeatId = "main",
                condition = StubExpression
            ))
        )
        val errors = scene.validate()
        assertTrue(errors.any { it.path == "transitions[0].sourceBeatId" })
    }

    @Test
    fun `transition referencing missing target beat is reported`() {
        val scene = Scene(
            id = "test",
            startBeatId = "main",
            beats = mapOf("main" to minimalBeat()),
            transitions = listOf(BeatTransition(
                sourceBeatId = "main",
                targetBeatId = "ghost",
                condition = StubExpression
            ))
        )
        val errors = scene.validate()
        assertTrue(errors.any { it.path == "transitions[0].targetBeatId" })
    }

    @Test
    fun `unreachable beat is a WARNING`() {
        val scene = Scene(
            id = "test",
            startBeatId = "main",
            beats = mapOf(
                "main" to minimalBeat(),
                "orphan" to minimalBeat(id = "orphan")
            )
        )
        val errors = scene.validate()
        val orphanError = errors.firstOrNull { it.path == "beats[orphan]" }
        assertNotNull(orphanError)
        assertEquals(ValidationError.Severity.WARNING, orphanError!!.severity)
    }

    @Test
    fun `blank scene id throws at construction`() {
        assertThrows(IllegalArgumentException::class.java) {
            Scene(id = "", startBeatId = "main", beats = mapOf("main" to minimalBeat()))
        }
    }

    @Test
    fun `unsupported versions throw at construction`() {
        for (version in listOf(0, 1, 3)) {
            assertThrows(IllegalArgumentException::class.java) {
                Scene(
                    id = "test",
                    startBeatId = "main",
                    beats = mapOf("main" to minimalBeat()),
                    version = version
                )
            }
        }
    }

    @Test
    fun `CutTrack with empty entries throws at construction`() {
        assertThrows(IllegalArgumentException::class.java) {
            CutTrack(entries = emptyList())
        }
    }

    @Test
    fun `CutTrack with out-of-order entries throws at construction`() {
        assertThrows(IllegalArgumentException::class.java) {
            CutTrack(entries = listOf(
                CutEntry(time = 5f, shotId = "a"),
                CutEntry(time = 1f, shotId = "b")
            ))
        }
    }

    @Test
    fun `beat transition cycle is a WARNING`() {
        val scene = Scene(
            id = "test",
            startBeatId = "a",
            beats = mapOf(
                "a" to minimalBeat(id = "a"),
                "b" to minimalBeat(id = "b")
            ),
            transitions = listOf(
                BeatTransition(sourceBeatId = "a", targetBeatId = "b", condition = StubExpression),
                BeatTransition(sourceBeatId = "b", targetBeatId = "a", condition = StubExpression)
            )
        )
        val errors = scene.validate()
        val cycleError = errors.firstOrNull { it.path == "transitions" && it.message == Component.translatable("cobblemon.cinematic_editor.validation.cycle", "a -> b -> a") }
        assertNotNull(cycleError)
        assertEquals(ValidationError.Severity.WARNING, cycleError!!.severity)
    }
}


private object StubPositionSource : PositionSource() {
    override val type: String = "stub"
}

private object StubRotationSource : RotationSource() {
    override val type: String = "stub"
}

private object StubExpression : ExpressionLike {
    override fun toString(): String = "0"
    override fun resolve(
        runtime: MoLangRuntime,
        context: Map<String, MoValue>
    ): MoValue =
        DoubleValue(0.0)
}
