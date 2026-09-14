package com.cobblemon.mod.common.client.editor.cinematic.overlay

import com.cobblemon.mod.common.api.cinematic.model.ActorKind
import com.cobblemon.mod.common.api.cinematic.runtime.ActorFraming
import com.cobblemon.mod.common.api.camera.model.CoordinateValue
import com.cobblemon.mod.common.api.camera.model.PositionExpression
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.KeyframeMovement
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.client.editor.cinematic.EditorPathCache
import com.cobblemon.mod.common.client.editor.cinematic.EditorRoutePreview
import com.cobblemon.mod.common.client.editor.cinematic.SceneEditorOverlay
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

object EditorActorBounds {
    private var cachedScene: EditorScene? = null
    private var generation = -1
    private val sizes = mutableMapOf<String, Pair<Double, Double>>()

    fun bounds(actorId: String): AABB? {
        SceneEditorOverlay.preview.actorBounds(actorId)?.let { return it }
        val scene = SceneEditorOverlay.activeScene ?: return null
        val actor = scene.actors[actorId] ?: return null
        if (cachedScene !== scene || generation != scene.editGeneration) {
            sizes.clear()
            cachedScene = scene
            generation = scene.editGeneration
        }
        val beatId = SceneEditorOverlay.selection.getSelectedBeatId() ?: scene.startBeatId
        val direction = scene.beats[beatId]?.actorDirections?.get(actorId)
        if (actor.kind == ActorKind.RealEntity &&
            actor.previewSpecies.isBlank() && direction == null) return null
        val time = SceneEditorOverlay.currentPlayheadSeconds
        var position = direction?.startPose?.position?.literalPosition()
            ?: if (direction?.startPose == null) SceneEditorOverlay.previewOrigin.add(actor.previewOffset) else null
        direction?.tracks?.forEachIndexed { index, track ->
            runCatching {
                position = when (track.trackType) {
                    "keyframe_movement" -> KeyframeMovement.fromJson(track.data).evaluate(time)
                    "spline_movement" -> EditorPathCache.actor("$beatId/$actorId/$index", track.data).evaluate(time)
                    "pathfinding" -> EditorRoutePreview.result(scene, beatId, actorId, index)?.frames?.let { frames ->
                        frames.getOrNull((time * 20).toInt().coerceIn(0, (frames.size - 1).coerceAtLeast(0)))?.position
                    } ?: position
                    else -> position
                }
            }
        }
        val (width, height) = sizes.getOrPut(actorId) {
            runCatching {
                if (actor.previewSpecies.isBlank()) return@runCatching 0.8 * actor.previewScale to 1.8 * actor.previewScale
                val pokemon = PokemonProperties.parse(actor.previewSpecies).create()
                pokemon.forcedAspects = pokemon.forcedAspects + actor.previewAspects
                val size = pokemon.form.hitbox.scale(pokemon.form.baseScale * pokemon.effectiveScale)
                size.width.toDouble() to size.height.toDouble()
            }.getOrDefault(0.8 to 1.8)
        }
        val resolvedPosition = position ?: return null
        return AABB(resolvedPosition.x - width / 2, resolvedPosition.y, resolvedPosition.z - width / 2,
            resolvedPosition.x + width / 2, resolvedPosition.y + height, resolvedPosition.z + width / 2)
    }

    fun bounds(actorIds: List<String>): AABB? = ActorFraming.expand(
        actorIds, SceneEditorOverlay.activeScene?.actors?.keys.orEmpty()).mapNotNull(::bounds).reduceOrNull(AABB::minmax)

    private fun PositionExpression.literalPosition(): Vec3? {
        val x = (this.x as? CoordinateValue.Static)?.value ?: return null
        val y = (this.y as? CoordinateValue.Static)?.value ?: return null
        val z = (this.z as? CoordinateValue.Static)?.value ?: return null
        return Vec3(x, y, z)
    }
}
