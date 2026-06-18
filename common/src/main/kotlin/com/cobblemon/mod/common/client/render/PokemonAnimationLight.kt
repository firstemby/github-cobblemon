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
import com.cobblemon.mod.common.client.render.models.blockbench.animation.ActiveAnimation
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockActiveAnimation
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.endsWith
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.nio.charset.StandardCharsets
import net.minecraft.server.packs.resources.ResourceManager

/**
 * Per-animation emissive light windows for Pokemon whose emissive parts only appear or flare during
 * specific animations (Pikachu cheeks, the Cyndaquil line's flames flaring in battle, Bellibolt's belly,
 * etc.). A Pokemon emits the window's block-light level only while its current animation's elapsed time
 * falls inside one of the windows. Consulted by the LambDynamicLights `PokemonLuminance` (so it only does
 * anything when that mod is installed).
 *
 * Data is loaded from JSON files under `assets/<namespace>/dynamiclights/entity/animations/`: a map of
 * species path -> animation short name -> list of `{ start, end, level }` (start/end in seconds into the
 * animation; looping animations wrap).
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
                    Cobblemon.LOGGER.error("Failed to load animation light config $identifier", e)
                }
            }
        data = loaded
        if (loaded.isNotEmpty()) Cobblemon.LOGGER.info("Loaded animation dynamic-light windows for ${loaded.size} species")
    }

    /** Block light (0..15) this Pokemon's emissive parts emit right now based on its active animation, or 0. */
    @JvmStatic
    fun luminanceFor(entity: PokemonEntity): Int {
        if (data.isEmpty()) return 0
        val perAnim = data[entity.exposedSpecies.resourceIdentifier.path] ?: return 0
        val state = entity.delegate as? PosableState ?: return 0
        val seconds = state.animationSeconds
        var best = 0
        fun consider(anim: ActiveAnimation?) {
            val bedrock = anim as? BedrockActiveAnimation ?: return
            val windows = perAnim[bedrock.animation.name.substringAfterLast('.')] ?: return
            var elapsed = seconds - bedrock.startedSeconds
            val length = bedrock.animation.animationLength.toFloat()
            if (bedrock.animation.shouldLoop && length > 0f) elapsed = (elapsed % length + length) % length
            for (w in windows) {
                if (elapsed >= w.start && elapsed <= w.end && w.level > best) best = w.level
            }
        }
        state.primaryAnimation?.let { consider(it.animation) }
        state.allActiveAnimations.forEach(::consider)
        return best
    }
}
