/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.registry

import com.cobblemon.mod.common.api.camera.model.HudElement
import com.cobblemon.mod.common.api.camera.result.ValidationError
import com.cobblemon.mod.common.api.cinematic.model.ActorKind
import com.cobblemon.mod.common.api.cinematic.model.DisconnectPolicy
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.api.cinematic.model.StartPoseMode
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SceneDeserializerTest {
    private val gson = GsonBuilder().registerTypeAdapter(Scene::class.java, SceneDeserializer).create()

    @BeforeEach
    fun registerSources() {
        SceneBuiltinSources.register()
    }

    @Test
    fun `bundled cinematic scenes use the canonical schema and validate`() {
        for (name in listOf("stadium_dynamic", "colosseum_battle")) {
            val source = requireNotNull(javaClass.getResourceAsStream("/data/cobblemon/cinematic_scenes/$name.json")) {
                "Missing bundled cinematic scene $name"
            }.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val parsed = gson.fromJson(source, Scene::class.java)
            val errors = parsed.validate().filter { it.severity == ValidationError.Severity.ERROR }
            assertTrue(errors.isEmpty(), "$name: $errors")
        }
    }

    @Test
    fun `canonical actor kinds parse and obsolete spellings fail`() {
        val kinds = mapOf("player_takeover" to ActorKind.PlayerTakeover, "real_entity" to ActorKind.RealEntity,
            "synthesized_pokemon" to ActorKind.SynthesizedPokemon, "synthesized_bedrock" to ActorKind.SynthesizedBedrock)
        for ((kind, expected) in kinds) {
            val json = scene()
            json.getAsJsonObject("actors").getAsJsonObject("hero").addProperty("kind", kind)
            assertEquals(expected, parse(json).actors.getValue("hero").kind)
            json.getAsJsonObject("actors").getAsJsonObject("hero").addProperty("kind", kind.replace("_", ""))
            assertThrows(JsonParseException::class.java) { parse(json) }
        }
    }

    @Test
    fun `sceneId is required and unsupported versions fail`() {
        val json = scene()
        json.add("id", json.remove("sceneId"))
        assertThrows(JsonParseException::class.java) { parse(json) }
        for (version in listOf("0", "1", "3", "2.5", "4294967298", "\"2\"", "null", "{}")) {
            val unsupported = scene().apply { add("version", JsonParser.parseString(version)) }
            assertThrows(JsonParseException::class.java) { parse(unsupported) }
        }
        assertThrows(JsonParseException::class.java) { parse(scene().apply { remove("version") }) }
    }

    @Test
    fun `unknown and untagged modifiers cannot be silently dropped`() {
        for (modifier in listOf(JsonObject(), JsonObject().apply { addProperty("type", "missing_modifier") })) {
            val json = scene()
            json.getAsJsonObject("shots").getAsJsonObject("wide").add("modifiers", JsonArray().apply { add(modifier) })
            val error = assertThrows(JsonParseException::class.java) { parse(json) }
            assertTrue(error.message.orEmpty().contains("wide"))
            assertTrue(error.message.orEmpty().contains("modifier"))
        }
    }

    @Test
    fun `unknown and untagged actor tracks cannot be silently dropped`() {
        for (track in listOf(JsonObject(), JsonObject().apply { addProperty("trackType", "missing_track") })) {
            val json = scene()
            direction(json).add("tracks", JsonArray().apply { add(track) })
            val error = assertThrows(JsonParseException::class.java) { parse(json) }
            assertTrue(error.message.orEmpty().contains("hero"))
            assertTrue(error.message.orEmpty().contains("main"))
        }
    }

    @Test
    fun `invalid enum settings fail while valid case-insensitive values remain supported`() {
        val json = scene()
        json.addProperty("disconnectPolicy", "substitute")
        direction(json).addProperty("startPoseMode", "teleport")
        val parsed = parse(json)
        assertEquals(DisconnectPolicy.SUBSTITUTE, parsed.disconnectPolicy)
        assertEquals(StartPoseMode.TELEPORT, parsed.beats.getValue("main").actorDirections.getValue("hero").startPoseMode)

        json.addProperty("disconnectPolicy", "misspelled")
        assertThrows(JsonParseException::class.java) { parse(json) }
        json.remove("disconnectPolicy")
        direction(json).addProperty("startPoseMode", "misspelled")
        assertThrows(JsonParseException::class.java) { parse(json) }
    }

    @Test
    fun `HUD configuration rejects unknown names and incorrect shapes`() {
        val json = scene()
        val beat = json.getAsJsonObject("beats").getAsJsonObject("main")
        beat.addProperty("hiddenHudElements", true)
        assertEquals(HudElement.ALL, parse(json).beats.getValue("main").hiddenHudElements)
        beat.add("hiddenHudElements", JsonArray().apply { add("hotbar") })
        assertEquals(setOf(HudElement.HOTBAR), parse(json).beats.getValue("main").hiddenHudElements)

        for (invalid in listOf(JsonObject(), JsonArray().apply { add("not_a_hud_element") })) {
            beat.add("hiddenHudElements", invalid)
            assertThrows(JsonParseException::class.java) { parse(json) }
        }
    }

    @Test
    fun `an explicitly authored GUI track must contain its actions array`() {
        val json = scene()
        json.getAsJsonObject("beats").getAsJsonObject("main").add("guiTrack", JsonObject())
        assertThrows(JsonParseException::class.java) { parse(json) }
        json.getAsJsonObject("beats").getAsJsonObject("main").getAsJsonObject("guiTrack").add("actions", JsonArray())
        assertTrue(requireNotNull(parse(json).beats.getValue("main").guiTrack).actions.isEmpty())
    }

    @Test
    fun `numeric parameter defaults must be finite`() {
        for (value in listOf("NaN", "Infinity", "-Infinity")) {
            for (definition in listOf(
                JsonObject().apply { addProperty("type", "number"); addProperty("default", value) },
                JsonObject().apply {
                    addProperty("type", "vec3")
                    add("default", JsonObject().apply { addProperty("x", 0); addProperty("y", value); addProperty("z", 0) })
                }
            )) {
                val json = scene().apply { add("parameters", JsonObject().apply { add("position", definition) }) }
                assertThrows(JsonParseException::class.java) { parse(json) }
            }
        }
    }

    private fun direction(json: JsonObject) = json.getAsJsonObject("beats").getAsJsonObject("main")
        .getAsJsonObject("actorDirections").getAsJsonObject("hero")

    private fun parse(json: JsonObject): Scene = gson.fromJson(json, Scene::class.java)

    private fun scene(): JsonObject = JsonParser.parseString("""
        {"version":2,"sceneId":"parser_test","startBeatId":"main",
         "actors":{"hero":{"kind":"real_entity"}},
         "shots":{"wide":{"position":{"type":"fixed","position":{"x":0,"y":0,"z":0}},
                            "rotation":{"type":"fixed","yaw":0,"pitch":0}}},
         "beats":{"main":{"actorDirections":{"hero":{}}}}}
    """.trimIndent()).asJsonObject
}
