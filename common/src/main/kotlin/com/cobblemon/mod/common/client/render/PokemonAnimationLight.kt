/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockActiveAnimation
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockAnimation
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockPoseAnimation
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.endsWith
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.nio.charset.StandardCharsets
import net.minecraft.server.packs.resources.ResourceManager

/**
 * Per-animation emissive light windows for Pokemon whose emissive parts only appear or flare during
 * specific animations (Pikachu cheeks, the Cyndaquil line's flames flaring in battle, Bellibolt's belly,
 * etc.). A Pokemon emits the window's block-light level only while a matching animation's elapsed time
 * falls inside one of the windows. Consulted by the LambDynamicLights `PokemonLuminance`.
 *
 * Matches three kinds of running animation: the current PRIMARY animation (moves like special/physical/
 * status), ACTIVE/stateful animations (cry/battle_cry), and the current POSE's idle animations
 * (battle_idle/air_idle/air_fly etc., which loop continuously). Data is loaded from JSON under
 * `assets/<namespace>/dynamiclights/entity/animations/`: species path -> animation short name ->
 * list of `{ start, end, level }` (seconds into the animation; looping/continuous anims wrap).
 */
object PokemonAnimationLight {
    private val GSON = GsonBuilder().create()
    private class Window(val start: Float, val end: Float, val level: Int)
    // species path -> animation short name -> windows
    private var data: Map<String, Map<String, List<Window>>> = emptyMap()

    fun load(resourceManager: ResourceManager) {
        // Only LambDynamicLights consumes these; skip entirely if it isn't installed.
        if (!Cobblemon.implementation.isModInstalled("lambdynlights")) {
            data = emptyMap()
            Cobblemon.LOGGER.info("[AnimLight] LambDynamicLights not installed; animation lights disabled")
            return
        }
        val loaded = mutableMapOf<String, MutableMap<String, List<Window>>>()
        resourceManager.listResources("dynamiclights/entity/animations") { path -> path.endsWith(".json") }
            .forEach { identifier, resource ->
                try {
                    resource.open().use { stream ->
                        val root = GSON.fromJson(String(stream.readAllBytes(), StandardCharsets.UTF_8), JsonObject::class.java)
                        for ((species, animsElement) in root.entrySet()) {
                            val animMap = loaded.getOrPut(species) { mutableMapOf() }
                            for ((animName, windowsElement) in animsElement.asJsonObject.entrySet()) {
                                animMap[animName] = windowsElement.asJsonArray.map { w ->
                                    val o = w.asJsonObject
                                    Window(o.get("start").asFloat, o.get("end").asFloat, o.get("level").asInt.coerceIn(0, 15))
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Cobblemon.LOGGER.error("[AnimLight] Failed to load animation light config $identifier", e)
                }
            }
        data = loaded
        Cobblemon.LOGGER.info("[AnimLight] loaded animation dynamic-light windows for ${loaded.size} species: ${loaded.keys}")
    }

    /** Block light (0..15) this Pokemon's emissive parts emit right now based on its active animation, or 0. */
    @JvmStatic
    fun luminanceFor(entity: PokemonEntity): Int {
        if (data.isEmpty()) return 0
        val perAnim = data[entity.exposedSpecies.resourceIdentifier.path] ?: return 0
        val state = entity.delegate as? PosableState ?: return 0
        val seconds = state.animationSeconds
        var best = 0

        // startedSeconds != null -> timed (primary/stateful); null -> continuous pose idle (use the loop clock)
        fun match(animation: BedrockAnimation?, startedSeconds: Float?) {
            animation ?: return
            val windows = perAnim[animation.name.substringAfterLast('.')] ?: return
            val length = animation.animationLength.toFloat()
            val elapsed: Float = if (startedSeconds != null && startedSeconds >= 0f) {
                val e = seconds - startedSeconds
                if (animation.shouldLoop && length > 0f) (e % length + length) % length else e
            } else if (length > 0f) {
                (seconds % length + length) % length
            } else {
                Float.NaN // continuous, no length info -> treat as always within its windows
            }
            for (w in windows) {
                val inWindow = elapsed.isNaN() || (elapsed >= w.start && elapsed <= w.end)
                if (inWindow && w.level > best) best = w.level
            }
        }

        // Move animations: primary (special/physical/status/volttackle) and stateful (cry/battle_cry).
        (state.primaryAnimation?.animation as? BedrockActiveAnimation)?.let { match(it.animation, it.startedSeconds) }
        state.allActiveAnimations.forEach { (it as? BedrockActiveAnimation)?.let { b -> match(b.animation, b.startedSeconds) } }
        // Pose idle animations (battle_idle / air_idle / air_fly etc.) run continuously off the pose, not as active animations.
        state.currentModel?.poses?.get(state.currentPose)?.animations?.forEach { poseAnim ->
            (poseAnim as? BedrockPoseAnimation)?.let { match(it.animation, null) }
        }
        return best
    }
}
