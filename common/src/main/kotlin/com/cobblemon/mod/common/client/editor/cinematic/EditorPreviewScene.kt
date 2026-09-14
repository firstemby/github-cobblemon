package com.cobblemon.mod.common.client.editor.cinematic

import com.cobblemon.mod.common.api.cinematic.model.ActorKind
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.SceneSerializer
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import net.minecraft.world.phys.Vec3

object EditorPreviewScene {
    fun build(editor: EditorScene, origin: Vec3): JsonObject {
        val root = SceneSerializer.toJson(editor)
        editor.actors.values.filter { it.kind == ActorKind.RealEntity && it.previewSpecies.isNotBlank() }.forEach { actor ->
            root.getAsJsonObject("actors").getAsJsonObject(actor.id).addProperty("kind", "synthesized_pokemon")
            root.getAsJsonObject("beats").entrySet().forEach { (_, value) ->
                val beat = value.asJsonObject
                val directions = beat.getAsJsonObject("actorDirections") ?: JsonObject().also { beat.add("actorDirections", it) }
                val direction = directions.getAsJsonObject(actor.id) ?: JsonObject().also { directions.add(actor.id, it) }
                if (!direction.has("startPose") && direction.get("visible")?.asBoolean != false) {
                    direction.addProperty("visible", true)
                    direction.addProperty("startPoseMode", "teleport")
                    val position = origin.add(actor.previewOffset)
                    direction.add("startPose", JsonObject().apply {
                        add("position", JsonObject().apply {
                            addProperty("x", position.x); addProperty("y", position.y); addProperty("z", position.z)
                        })
                        add("rotation", JsonObject().apply { addProperty("yaw", 0); addProperty("pitch", 0) })
                    })
                }
            }
        }
        val parameters = root.getAsJsonObject("parameters")
        fun actorPosition(id: String) = editor.actors[id]?.takeIf { it.previewSpecies.isNotBlank() }
            ?.let { origin.add(it.previewOffset) }
        val trainer1 = actorPosition("trainer_1")
        val trainer2 = actorPosition("trainer_2")
        val arenaCenter = editor.shots.values.firstNotNullOfOrNull { shot ->
            shot.positionData.takeIf {
                (it.get("fitArena") as? JsonPrimitive)?.takeIf { value -> value.isBoolean }?.asBoolean == true &&
                    it.has("arenaWidth") && it.has("arenaDepth")
            }?.get("center")?.takeIf { it.isJsonObject }?.asJsonObject?.let(::literalPosition)
        }
        val points = mapOf(
            "arena_center" to (arenaCenter ?: if (trainer1 != null && trainer2 != null) trainer1.add(trainer2).scale(0.5) else null),
            "send_out_position_p1" to actorPosition("pokemon_1a"),
            "send_out_position_p2" to actorPosition("pokemon_2a")
        )
        points.forEach { (name, position) ->
            val parameter = parameters?.getAsJsonObject(name)
            if (position != null && parameter?.get("type")?.asString == "vec3") {
                parameter.add("default", JsonObject().apply {
                    addProperty("x", position.x); addProperty("y", position.y); addProperty("z", position.z)
                })
            }
        }
        return root
    }

    private fun literalPosition(value: JsonObject): Vec3? {
        fun coordinate(axis: String) = (value.get(axis) as? JsonPrimitive)?.takeIf { it.isNumber }
            ?.asDouble?.takeIf { it.isFinite() }
        return Vec3(coordinate("x") ?: return null, coordinate("y") ?: return null, coordinate("z") ?: return null)
    }
}
