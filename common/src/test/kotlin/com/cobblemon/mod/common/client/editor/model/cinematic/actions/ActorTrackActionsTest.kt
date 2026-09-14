package com.cobblemon.mod.common.client.editor.model.cinematic.actions

import com.cobblemon.mod.common.api.cinematic.model.ActorKind
import com.cobblemon.mod.common.client.editor.model.EditorTrack
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorActor
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorActorDirection
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorBeat
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ActorTrackActionsTest {

    private fun sceneWithActorDirection(): EditorScene {
        val scene = EditorScene(id = "test:scene", startBeatId = "main")
        scene.actors["hero"] = EditorActor(id = "hero", kind = ActorKind.RealEntity)
        val beat = EditorBeat(id = "main", duration = 10f)
        beat.actorDirections["hero"] = EditorActorDirection()
        scene.beats["main"] = beat
        return scene
    }

    @Test
    fun `AddActorTrack appends and undo removes`() {
        val scene = sceneWithActorDirection()
        val track = EditorTrack("look_at", JsonObject().apply {
            add("target", JsonObject().apply { addProperty("x", 0.0); addProperty("y", 64.0); addProperty("z", 0.0) })
        })

        scene.execute(AddActorTrack("main", "hero", track))
        assertEquals(1, scene.beats["main"]!!.actorDirections["hero"]!!.tracks.size)

        scene.undo()
        assertEquals(0, scene.beats["main"]!!.actorDirections["hero"]!!.tracks.size)
    }

    @Test
    fun `RemoveActorTrack drops entry at index and undo restores it`() {
        val scene = sceneWithActorDirection()
        val a = EditorTrack("attach", JsonObject().apply { addProperty("parentActorId", "x") })
        val b = EditorTrack("pose_lock", JsonObject().apply { addProperty("pose", "STAND") })
        scene.execute(AddActorTrack("main", "hero", a))
        scene.execute(AddActorTrack("main", "hero", b))

        scene.execute(RemoveActorTrack("main", "hero", 0, a))
        assertEquals(1, scene.beats["main"]!!.actorDirections["hero"]!!.tracks.size)
        assertEquals("pose_lock", scene.beats["main"]!!.actorDirections["hero"]!!.tracks[0].trackType)

        scene.undo()
        assertEquals(2, scene.beats["main"]!!.actorDirections["hero"]!!.tracks.size)
        assertEquals("attach", scene.beats["main"]!!.actorDirections["hero"]!!.tracks[0].trackType)
    }

    @Test
    fun `ChangeActorTrackData swaps body JSON and undo restores it`() {
        val scene = sceneWithActorDirection()
        val initial = JsonObject().apply { addProperty("parentActorId", "x") }
        val replacement = JsonObject().apply { addProperty("parentActorId", "y") }
        scene.execute(AddActorTrack("main", "hero", EditorTrack("attach", initial)))

        scene.execute(ChangeActorTrackData("main", "hero", 0, initial, replacement))
        assertEquals("y", scene.beats["main"]!!.actorDirections["hero"]!!.tracks[0].data.get("parentActorId").asString)

        scene.undo()
        assertEquals("x", scene.beats["main"]!!.actorDirections["hero"]!!.tracks[0].data.get("parentActorId").asString)
    }

    @Test fun `nested track values survive multiple edits and redo`() {
        val scene = sceneWithActorDirection()
        fun target(x: Int) = JsonObject().apply { add("target", JsonObject().apply { addProperty("x", x) }) }
        val initial = target(0)
        val track = EditorTrack("look_at", initial.deepCopy())
        scene.beats["main"]!!.actorDirections["hero"]!!.tracks.add(track)
        scene.execute(ChangeActorTrackData("main", "hero", 0, track.data, target(1)))
        val before = track.data.deepCopy()
        track.data.getAsJsonObject("target").addProperty("x", 2)
        scene.execute(ChangeActorTrackData("main", "hero", 0, before, track.data))
        scene.undo()
        assertEquals(target(1), track.data)
        scene.undo()
        assertEquals(initial, track.data)
        scene.redo()
        assertEquals(target(1), track.data)
        scene.redo()
        assertEquals(target(2), track.data)
    }

}
