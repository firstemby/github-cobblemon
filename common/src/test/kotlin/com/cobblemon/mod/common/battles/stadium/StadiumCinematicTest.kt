package com.cobblemon.mod.common.battles.stadium

import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.camera.result.ValidationError
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.ExpressionVec3Position
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.FixedFov
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.LookAt
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.OrbitPosition
import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.SmoothingModifier
import com.cobblemon.mod.common.api.cinematic.registry.SceneSourceRegistry
import com.cobblemon.mod.common.api.cinematic.runtime.BeatStateMachine
import com.cobblemon.mod.common.client.editor.cinematic.EditorPreviewScene
import com.cobblemon.mod.common.client.editor.model.cinematic.SceneSerializer
import com.google.gson.JsonParser
import net.minecraft.resources.ResourceLocation
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StadiumCinematicTest {
    @Test fun `stadium preview uses arena and every custom trainer and pokemon placement`() {
        val source = template()
        val origin = Vec3(100.0, 70.0, -200.0)
        for (count in 3..5) {
            val data = StadiumBlockData.createDefault().apply {
                cinematicLayout = "custom"
                arena = StadiumArena(origin.add(2.0, 0.0, 1.0), 24.0, 16.0)
                setLayout("custom", StadiumBlockLayout.createDefault().apply {
                    trainerSlots = count; pokemonSlots = count
                    listOf(side1, side2).forEachIndexed { side, placements ->
                        repeat(count) { slot ->
                            placements.actors.add(StadiumBlockPlacement(origin.add(if (side == 0) -7.0 else 7.0, 0.0, slot.toDouble())))
                            placements.pokemon.add(StadiumBlockPlacement(origin.add(if (side == 0) -3.0 else 3.0, 0.0, slot.toDouble())))
                        }
                    }
                })
            }
            val prepared = StadiumCinematic.forEditor(source, data, origin)
            val json = JsonParser.parseString(prepared).asJsonObject
            val scene = StadiumCinematic.parse(prepared)
            val editor = SceneSerializer.fromScene(scene, sourceJson = json)
            val orbit = scene.shots.getValue("arena_orbit").position as OrbitPosition
            assertEquals(data.arena!!.center, orbit.center)
            assertEquals(24f, orbit.arenaWidth)
            assertEquals(16f, orbit.arenaDepth)
            assertEquals(Vec3(7.0, 0.0, (count - 1).toDouble()), editor.actors.getValue("trainer_2_$count").previewOffset)
            assertEquals(Vec3(3.0, 0.0, (count - 1).toDouble()), editor.actors.getValue("pokemon_2${'a' + count - 1}").previewOffset)
            assertEquals(4 * count, editor.actors.values.count { it.id !in setOf("user", "target") && it.previewSpecies.isNotBlank() })
            val runtime = StadiumCinematic.fitArena(StadiumCinematic.parse(source), data.arena)
            assertEquals(orbit, runtime.shots.getValue("arena_orbit").position)
            assertEquals(orbit, StadiumCinematic.parse(SceneSerializer.toJson(editor).toString()).shots.getValue("arena_orbit").position)
            val preview = EditorPreviewScene.build(editor, origin)
            assertEquals(data.arena!!.center.x, preview.getAsJsonObject("parameters").getAsJsonObject("arena_center")
                .getAsJsonObject("default").get("x").asDouble)
        }
        assertNull((StadiumCinematic.parse(source).shots.getValue("arena_orbit").position as OrbitPosition).arenaWidth)
    }

    private fun template(): String {
        SceneSourceRegistry.registerPositionSource("orbit", OrbitPosition::fromJson)
        SceneSourceRegistry.registerPositionSource("expression_vec3", ExpressionVec3Position::fromJson)
        SceneSourceRegistry.registerRotationSource("look_at", LookAt::fromJson)
        SceneSourceRegistry.registerFovSource("fixed", FixedFov::fromJson)
        SceneSourceRegistry.registerModifier("smoothing", SmoothingModifier::fromJson)
        return checkNotNull(javaClass.getResourceAsStream("/data/cobblemon/cinematic_scenes/stadium_dynamic.json")).bufferedReader().use { it.readText() }
    }

    @Test fun `adaptive template validates and survives ImGui round trip`() {
        val source = template()
        val scene = StadiumCinematic.parse(source)
        assertEquals("cobblemon:stadium_dynamic", scene.id)
        assertEquals(7, scene.shots.size)
        assertTrue(scene.validate().none { it.severity == ValidationError.Severity.ERROR }, scene.validate().toString())
        val editor = SceneSerializer.fromScene(scene, sourceJson = JsonParser.parseString(source).asJsonObject)
        val restored = StadiumCinematic.parse(SceneSerializer.toJson(editor).toString())
        assertEquals(scene.shots.getValue("arena_orbit").position, restored.shots.getValue("arena_orbit").position)
        assertEquals(-8.0, editor.actors.getValue("trainer_1").previewOffset.x)
        val before = JsonParser.parseString(source).asJsonObject
        val after = SceneSerializer.toJson(editor)
        assertEquals(before.getAsJsonObject("beats").getAsJsonObject("intro").get("guiTrack"),
            after.getAsJsonObject("beats").getAsJsonObject("intro").get("guiTrack"))
        assertEquals(scene.feeds, restored.feeds)
        assertEquals(scene.transitions.size, restored.transitions.size)
        scene.beats.forEach { (id, beat) ->
            assertEquals(beat.hiddenHudElements, restored.beats.getValue(id).hiddenHudElements)
            assertEquals(beat.renderPlayerBody, restored.beats.getValue(id).renderPlayerBody)
        }
    }

    @Test fun `adaptive sequence preserves original intro and battle event graph`() {
        val dynamic = JsonParser.parseString(template()).asJsonObject
        val original = checkNotNull(javaClass.getResourceAsStream("/data/cobblemon/cinematic_scenes/colosseum_battle.json"))
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        assertEquals(original.get("feeds"), dynamic.get("feeds"))
        val intro = dynamic.getAsJsonObject("beats").getAsJsonObject("intro")
        val originalIntro = original.getAsJsonObject("beats").getAsJsonObject("intro")
        listOf("duration", "cues", "guiTrack", "hiddenHudElements", "renderPlayerBody").forEach {
            assertEquals(originalIntro.get(it), intro.get(it), it)
        }
        assertEquals(setOf("intro_side_a", "intro_side_b"), intro.getAsJsonObject("cuts").keySet())
        val beats = original.getAsJsonObject("beats").deepCopy()
        val orbit = beats.remove("idle_orbit").asJsonObject
        orbit.addProperty("id", "orbit")
        beats.add("orbit", orbit)
        val shotNames = mapOf("intro_shot" to "trainer_1", "intro_shot_side_b" to "trainer_2",
            "orbit_shot" to "arena_orbit", "user_focus_shot" to "user_focus", "target_focus_shot" to "target_focus")
        beats.entrySet().forEach { (_, beat) ->
            beat.asJsonObject.getAsJsonObject("cuts").entrySet().forEach { (_, track) ->
                track.asJsonObject.getAsJsonArray("entries").forEach { entry ->
                    val cut = entry.asJsonObject
                    shotNames[cut.get("shotId").asString]?.let { cut.addProperty("shotId", it) }
                }
            }
        }
        assertEquals(beats, dynamic.get("beats"))
        val transitions = original.getAsJsonArray("transitions").deepCopy()
        transitions.forEach { entry ->
            val transition = entry.asJsonObject
            listOf("sourceBeatId", "targetBeatId").forEach {
                if (transition.get(it).asString == "idle_orbit") transition.addProperty(it, "orbit")
            }
        }
        assertEquals(transitions, dynamic.get("transitions"))
    }

    @Test fun `send outs preempt battle focus and both focus roles can switch directly`() {
        val machine = BeatStateMachine(StadiumCinematic.parse(template()))
        assertTrue(machine.start())
        fun variable(name: String, value: Int) { machine.runtime.environment.variable.map[name] = DoubleValue(value.toDouble()) }
        fun advance(expected: String) { machine.advance(0.05f); assertEquals(expected, machine.currentBeat?.id) }
        variable("battle_launched", 1)
        advance("orbit")
        variable("send_out_pending_p1", 1)
        variable("send_out_pending_p2", 1)
        advance("send_out_focus_p1")
        variable("send_out_pending_p1", 0)
        advance("send_out_focus_p2")
        variable("send_out_pending_p2", 0)
        advance("orbit")
        variable("camera_focus_count", 1)
        variable("camera_focus_role", 0)
        advance("user_focus")
        variable("camera_focus_role", 1)
        advance("target_focus")
        variable("camera_focus_role", 0)
        advance("user_focus")
        variable("send_out_pending_p2", 1)
        advance("send_out_focus_p2")
    }

    @Test fun `send out previews use actor positions instead of world origin`() {
        val source = template()
        val editor = SceneSerializer.fromScene(StadiumCinematic.parse(source), sourceJson = JsonParser.parseString(source).asJsonObject)
        val before = SceneSerializer.toJson(editor)
        val preview = EditorPreviewScene.build(editor, Vec3(100.0, 70.0, 200.0))
        assertEquals(before, SceneSerializer.toJson(editor))
        val parameters = preview.getAsJsonObject("parameters")
        fun x(name: String) = parameters.getAsJsonObject(name).getAsJsonObject("default").get("x").asDouble
        assertEquals(100.0, x("arena_center"))
        assertEquals(97.0, x("send_out_position_p1"))
        assertEquals(103.0, x("send_out_position_p2"))
    }

    @Test fun `block scoped override preserves template and persists through NBT`() {
        val source = template()
        val id = ResourceLocation.parse("cobblemon:stadium/test")
        val normalized = StadiumCinematic.normalize(source, id)
        assertEquals(id.toString(), StadiumCinematic.parse(normalized).id)
        assertEquals("cobblemon:stadium_dynamic", StadiumCinematic.parse(source).id)
        val block = StadiumBlockData.createDefault().apply { cinematicScene = id; cinematicSceneJson = normalized }
        val reloaded = StadiumBlockData.fromNbt(block.toNbt())
        assertEquals(normalized, reloaded.cinematicSceneJson)
        assertNotNull(StadiumCinematic.resolve(reloaded.cinematicScene, reloaded.cinematicSceneJson))
        assertNull(StadiumCinematic.resolve(ResourceLocation.parse("cobblemon:wrong"), normalized))
    }

    @Test fun `missing launch cue and oversized scenes cannot be applied`() {
        val source = template().replace("\"battle_launch\"", "\"other_cue\"")
        val missingCue = assertThrows(StadiumCinematic.ValidationException::class.java) { StadiumCinematic.parse(source) }
        assertEquals("cobblemon.stadium.cinematic.launch_cue", (missingCue.reason.contents as TranslatableContents).key)
        val oversized = assertThrows(StadiumCinematic.ValidationException::class.java) { StadiumCinematic.parse(" ".repeat(StadiumCinematic.MAX_LENGTH + 1)) }
        assertEquals("cobblemon.stadium.cinematic.too_large", (oversized.reason.contents as TranslatableContents).key)
    }

    @Test fun `launch cue in disconnected intro cannot be applied`() {
        val source = JsonParser.parseString(template()).asJsonObject
        source.addProperty("startBeatId", "orbit")

        val error = assertThrows(StadiumCinematic.ValidationException::class.java) { StadiumCinematic.parse(source.toString()) }

        assertEquals("cobblemon.stadium.cinematic.launch_cue", (error.reason.contents as TranslatableContents).key)
    }

    @Test fun `removing arena clears fitted dimensions in runtime and editor`() {
        val source = template()
        val data = StadiumBlockData.createDefault().apply { arena = StadiumArena(Vec3.ZERO, 24.0, 16.0) }
        val fitted = StadiumCinematic.forEditor(source, data, Vec3.ZERO)
        val runtime = StadiumCinematic.fitArena(StadiumCinematic.parse(fitted), null)
        data.arena = null
        val editor = StadiumCinematic.parse(StadiumCinematic.forEditor(fitted, data, Vec3.ZERO))

        listOf(runtime, editor).forEach {
            val orbit = it.shots.getValue("arena_orbit").position as OrbitPosition
            assertNull(orbit.arenaWidth)
            assertNull(orbit.arenaDepth)
        }
    }
}
