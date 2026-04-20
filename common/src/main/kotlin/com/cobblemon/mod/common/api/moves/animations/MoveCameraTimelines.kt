/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.moves.animations

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.moves.animations.keyframes.ActionEffectKeyframe
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.pokemon.adapters.CobblemonStatTypeAdapter
import com.cobblemon.mod.common.util.adapters.*
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.advancements.critereon.MinMaxBounds
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.awt.Color

/**
 * Data registry containing camera action effect timelines for battle moves.
 *
 * These camera timelines run in parallel with the main action effects timeline,
 * allowing camera work to be independent from move timing and execution.
 *
 * Camera timeline files are stored at: `data/<namespace>/action_effects/moves/camera/<movename>_camera.json`
 *
 * Example structure:
 * ```json
 * {
 *   "timeline": [
 *     {
 *       "type": "camera_focus",
 *       "source": "USERS",
 *       "duration": 2
 *     },
 *     {
 *       "type": "pause",
 *       "pause": 0.5
 *     },
 *     {
 *       "type": "camera_focus",
 *       "source": "TARGETS",
 *       "duration": 2
 *     }
 *   ]
 * }
 * ```
 *
 * @author Terrainwax
 * @since January 2025
 */
object MoveCameraTimelines : JsonDataRegistry<ActionEffectTimeline> {
    override val id: ResourceLocation = cobblemonResource("move_camera_timelines")
    override val type: PackType = PackType.SERVER_DATA
    override val observable = SimpleObservable<MoveCameraTimelines>()

    override val gson = GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .registerTypeAdapter(ActionEffectKeyframe::class.java, ActionEffectKeyframeAdapter)
        .registerTypeAdapter(MinMaxBounds.Doubles::class.java, FloatNumberRangeAdapter)
        .registerTypeAdapter(TypeToken.getParameterized(Collection::class.java, AABB::class.java).type, BoxCollectionAdapter)
        .registerTypeAdapter(AABB::class.java, BoxAdapter)
        .registerTypeAdapter(Vec3::class.java, VerboseVec3dAdapter)
        .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
        .registerTypeAdapter(IntRange::class.java, VerboseIntRangeAdapter)
        .registerTypeAdapter(Color::class.java, LiteralHexColorAdapter)
        .registerTypeAdapter(Stat::class.java, CobblemonStatTypeAdapter)
        .registerTypeAdapter(Expression::class.java, ExpressionAdapter)
        .registerTypeAdapter(ExpressionLike::class.java, ExpressionLikeAdapter)
        .registerTypeAdapter(
            TypeToken.getParameterized(
                TypeToken.get(List::class.java).type,
                TypeToken.get(ActionEffectKeyframe::class.java).type
            ).type,
            SingleToPluralAdapter(ActionEffectKeyframe::class.java) { it }
        )
        .create()

    override val typeToken: TypeToken<ActionEffectTimeline> = TypeToken.get(ActionEffectTimeline::class.java)
    override val resourcePath = "action_effects/moves/camera"

    private val cameraTimelines = mutableMapOf<ResourceLocation, ActionEffectTimeline>()

    override fun reload(data: Map<ResourceLocation, ActionEffectTimeline>) {
        cameraTimelines.clear()
        cameraTimelines.putAll(data)
        observable.emit(this)
    }

    override fun sync(player: ServerPlayer) {
        // Camera timelines are server-side data, no sync needed
    }

    /**
     * Gets a camera timeline for a specific move.
     *
     * @param moveId The move identifier (e.g., "cobblemon:flameburst")
     * @return The camera action effect timeline, or null if not found
     */
    fun getCameraTimeline(moveId: ResourceLocation): ActionEffectTimeline? {
        // Try direct match first (e.g., "cobblemon:flameburst_camera")
        val directId = ResourceLocation(moveId.namespace, "${moveId.path}_camera")
        if (cameraTimelines.containsKey(directId)) {
            return cameraTimelines[directId]
        }

        // Try without namespace (e.g., just "flameburst_camera")
        val simpleId = cobblemonResource("${moveId.path}_camera")
        if (cameraTimelines.containsKey(simpleId)) {
            return cameraTimelines[simpleId]
        }

        // Fallback to generic_move_camera if move-specific camera not found
        val genericId = cobblemonResource("generic_move_camera")
        return cameraTimelines[genericId]
    }

    /**
     * Checks if a camera timeline exists for a move.
     *
     * @param moveId The move identifier
     * @return True if a camera timeline exists for this move
     */
    fun hasCameraTimeline(moveId: ResourceLocation): Boolean {
        return getCameraTimeline(moveId) != null
    }

    /**
     * Gets all registered camera timeline IDs.
     */
    fun getTimelineIds(): Set<ResourceLocation> = cameraTimelines.keys.toSet()
}
