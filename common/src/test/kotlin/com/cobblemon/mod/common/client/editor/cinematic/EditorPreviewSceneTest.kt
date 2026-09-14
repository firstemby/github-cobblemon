package com.cobblemon.mod.common.client.editor.cinematic

import com.cobblemon.mod.common.api.cinematic.model.ActorKind
import com.cobblemon.mod.common.api.cinematic.model.ActorBinding
import com.cobblemon.mod.common.api.camera.model.ParameterDefault
import com.cobblemon.mod.common.api.camera.model.ParameterDefinition
import com.cobblemon.mod.common.api.camera.model.ParameterType
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorShot
import com.google.gson.JsonParser
import com.cobblemon.mod.common.api.cinematic.model.StartPoseMode
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorActor
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.SceneSerializer
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.api.cinematic.registry.SceneDeserializer
import com.google.gson.GsonBuilder
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EditorPreviewSceneTest {
    @Test fun `real roles get synthetic preview poses without changing authored data`() {
        val editor = EditorScene.createDefault()
        editor.actors["trainer"] = EditorActor("trainer", ActorKind.RealEntity, previewSpecies = "pikachu", previewOffset = Vec3(8.0, 0.0, 2.0))
        editor.actors["player"] = EditorActor("player", ActorKind.PlayerTakeover)
        val before = SceneSerializer.toJson(editor)
        val source = EditorPreviewScene.build(editor, Vec3(50.0, 70.0, 100.0))
        assertEquals(before, SceneSerializer.toJson(editor))
        val scene = GsonBuilder().registerTypeAdapter(Scene::class.java, SceneDeserializer).create().fromJson(source, Scene::class.java)
        assertSame(ActorKind.SynthesizedPokemon, scene.actors.getValue("trainer").kind)
        assertSame(ActorKind.PlayerTakeover, scene.actors.getValue("player").kind)
        val direction = scene.beats.getValue("main").actorDirections.getValue("trainer")
        assertEquals(StartPoseMode.TELEPORT, direction.startPoseMode)
        assertEquals(true, direction.visible)
        assertEquals(58.0, source.getAsJsonObject("beats").getAsJsonObject("main").getAsJsonObject("actorDirections")
            .getAsJsonObject("trainer").getAsJsonObject("startPose").getAsJsonObject("position").get("x").asDouble)
        assertFalse(scene.beats.getValue("main").actorDirections.containsKey("player"))
    }

    @Test fun `expression orbit center preserves parameter defaults and authored expression`() {
        val editor = EditorScene.createDefault()
        editor.parameters["arena_center"] = ParameterDefinition(ParameterType.VEC3,
            default = ParameterDefault.Vec3Default(12.0, 70.0, 18.0))
        val data = JsonParser.parseString("""{"type":"orbit","fitArena":true,"arenaWidth":20,"arenaDepth":16,
            "center":{"x":"v.arena_center_x","y":70,"z":18}}""").asJsonObject
        editor.shots["orbit"] = EditorShot("orbit", data, JsonParser.parseString("""{"type":"fixed","yaw":0,"pitch":0}""").asJsonObject)
        val before = SceneSerializer.toJson(editor)
        val result = EditorPreviewScene.build(editor, Vec3.ZERO)
        assertEquals(before.getAsJsonObject("parameters"), result.getAsJsonObject("parameters"))
        assertEquals(before, SceneSerializer.toJson(editor))
        assertEquals("v.arena_center_x", result.getAsJsonObject("shots").getAsJsonObject("orbit")
            .getAsJsonObject("position").getAsJsonObject("center").get("x").asString)
    }

    @Test fun `nonfinite bedrock preview scale uses the existing invalid scale default`() {
        val editor = EditorScene.createDefault()
        editor.actors["prop"] = EditorActor("prop", ActorKind.SynthesizedBedrock,
            previewCategory = "test:prop", previewScale = Float.POSITIVE_INFINITY)
        val binding = EditorActorBindingProvider.collect(editor).getValue("prop") as ActorBinding.SynthBedrock
        assertEquals(1f, binding.scale)
    }
}
