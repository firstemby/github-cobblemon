/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime

import net.minecraft.network.chat.Component
import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.BeatTransition
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.api.cinematic.model.SceneCue
import com.cobblemon.mod.common.util.asExpressionLike
import java.util.UUID
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SceneCuePlaybackTest {
    private fun instance(beats: List<Beat>, transitions: List<BeatTransition> = emptyList(), preview: Boolean = false): SceneInstance {
        val scene = Scene("cues", startBeatId = beats.first().id, beats = beats.associateBy { it.id }, transitions = transitions)
        return SceneInstance(UUID.randomUUID(), scene, Vec3.ZERO, emptyMap(), previewMode = preview).also { assertTrue(it.start()) }
    }

    @Test
    fun `cue fires at authored time before its transition and only once`() {
        val intro = Beat("intro", duration = 3f, speed = 2f, cues = listOf(SceneCue("battle_launch", 3f)))
        val scene = instance(listOf(intro, Beat("idle")),
            listOf(BeatTransition("intro", "idle", "v.battle_launched == 1".asExpressionLike())))
        var launches = 0
        scene.onCue { launches++; scene.setVariable("battle_launched", 1.0) }
        repeat(2) { scene.tick(0.5f) }
        assertEquals(0, launches)
        scene.tick(0.5f)
        assertEquals(1, launches)
        assertEquals("idle", scene.stateMachine.currentBeat?.id)
        repeat(5) { scene.tick(0.5f) }
        assertEquals(1, launches)
        scene.stop()
    }

    @Test
    fun `zero-time and same-time cues use stable order including beat entry`() {
        val intro = Beat("intro", cues = listOf(SceneCue("second", 0.2f), SceneCue("first", 0f), SceneCue("third", 0.2f)))
        val next = Beat("next", cues = listOf(SceneCue("entry", 0f)))
        val scene = instance(listOf(intro, next),
            listOf(BeatTransition("intro", "next", "v.time_on_current_path >= 0.2".asExpressionLike())))
        val events = mutableListOf<String>()
        scene.onCue { events += it.id }
        scene.tick(0.2f)
        assertEquals(listOf("first", "second", "third", "entry"), events)
        scene.stop()
    }

    @Test
    fun `early transition does not flush future cues`() {
        val scene = instance(listOf(Beat("intro", duration = 4f, cues = listOf(SceneCue("late", 3f))), Beat("next")),
            listOf(BeatTransition("intro", "next", "v.time_on_current_path >= 0.5".asExpressionLike())))
        val events = mutableListOf<String>()
        scene.onCue { events += it.id }
        repeat(10) { scene.tick(0.5f) }
        assertTrue(events.isEmpty())
        scene.stop()
    }

    @Test
    fun `cue at end dispatches before scene teardown`() {
        val scene = instance(listOf(Beat("intro", duration = 1f, cues = listOf(SceneCue("end", 1f)))),
            listOf(BeatTransition("intro", "", "v.time_on_current_path >= 1".asExpressionLike(), endsScene = true)))
        val events = mutableListOf<String>()
        scene.onCue { events += it.id }
        scene.onStop { events += "stopped" }
        repeat(4) { scene.tick(0.5f) }
        scene.stop()
        assertEquals(listOf("end", "stopped"), events)
    }

    @Test
    fun `looping does not repeat one-shot cues`() {
        val scene = instance(listOf(Beat("loop", duration = 1f, isLooping = true, cues = listOf(SceneCue("once", 0.75f)))))
        var events = 0
        scene.onCue { events++ }
        repeat(12) { scene.tick(0.5f) }
        assertEquals(1, events)
        scene.stop()
    }

    @Test
    fun `self transitions do not repeat one-shot cues`() {
        val scene = instance(listOf(Beat("main", cues = listOf(SceneCue("once", 0f)))),
            listOf(BeatTransition("main", "main", "v.time_on_current_path >= 1".asExpressionLike())))
        var events = 0
        scene.onCue { events++ }
        repeat(8) { scene.tick(0.5f) }
        assertEquals(1, events)
        scene.stop()
    }

    @Test
    fun `failed listener stops safely without dispatching subsequent cues`() {
        val scene = instance(listOf(Beat("main", cues = listOf(SceneCue("fail", 0f), SceneCue("later", 0f)))))
        var attempts = 0
        var stops = 0
        scene.onCue { attempts++; error("test failure") }
        scene.onStop { stops++ }
        scene.tick(0.5f)
        scene.tick(0.5f)
        assertEquals(1, attempts)
        assertEquals(1, stops)
        assertFalse(scene.isActive())
    }

    @Test
    fun `stopping from a cue cancels remaining callbacks and restores once`() {
        val scene = instance(listOf(Beat("main", cues = listOf(SceneCue("cancel", 0f), SceneCue("later", 0f)))))
        val events = mutableListOf<String>()
        var restores = 0
        scene.onStop { restores++ }
        scene.onCue { events += it.id; scene.stop() }
        scene.onCue { fail<Unit>("Listener after cancellation must not run") }
        scene.tick(0.5f)
        scene.stop()
        scene.tick(0.5f)
        assertEquals(listOf("cancel"), events)
        assertEquals(1, restores)
    }

    @Test
    fun `preview seeking and playback never dispatch cues`() {
        val scene = instance(listOf(Beat("main", duration = 4f, cues = listOf(SceneCue("battle_launch", 2f)))), preview = true)
        var events = 0
        scene.onCue { events++ }
        for (time in listOf(3f, 1f, 4f, 0f)) {
            scene.configurePreview("main", time, true)
            scene.tick(0.5f)
        }
        scene.configurePreview("main", 0f, false)
        repeat(10) { scene.tick(0.5f) }
        assertEquals(0, events)
        scene.stop()
    }

    @Test
    fun `stopping before the launch cue cancels it permanently`() {
        val scene = instance(listOf(Beat("intro", duration = 4f, cues = listOf(SceneCue("battle_launch", 3f)))))
        var launches = 0
        var restores = 0
        scene.onCue { launches++ }
        scene.onStop { restores++ }
        scene.tick(0.5f)
        scene.stop()
        repeat(10) { scene.tick(0.5f) }
        scene.stop()
        assertEquals(0, launches)
        assertEquals(1, restores)
    }

    @Test
    fun `invalid cue ids times and duplicate scene ids are rejected`() {
        for (time in listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { SceneCue("cue", time) }
        }
        assertThrows(IllegalArgumentException::class.java) { SceneCue("", 0f) }
        val scene = Scene("invalid", startBeatId = "main", beats = mapOf(
            "main" to Beat("main", duration = 1f, cues = listOf(SceneCue("cue", 2f))),
            "other" to Beat("other", cues = listOf(SceneCue("cue", 0f)))))
        assertTrue(scene.validate().any { it.message == Component.translatable("cobblemon.cinematic_editor.validation.late_cue", "cue") })
        assertTrue(scene.validate().any { it.message == Component.translatable("cobblemon.cinematic_editor.validation.duplicate_cue", "cue") })
    }
}
