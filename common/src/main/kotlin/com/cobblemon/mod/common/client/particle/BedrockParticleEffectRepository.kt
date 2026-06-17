/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.particle

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.Cobblemon.LOGGER
import com.cobblemon.mod.common.api.snowstorm.BedrockParticleOptions
import com.cobblemon.mod.common.particle.SnowstormParticleReader
import com.cobblemon.mod.common.util.endsWith
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.nio.charset.StandardCharsets
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager

/**
 * Client-side storage of loaded [BedrockParticleOptions]s.
 *
 * @author Hiroku
 * @since February 11th, 2023
 */
object BedrockParticleOptionsRepository {
    private val GSON = GsonBuilder().create()
    private val effects = mutableMapOf<ResourceLocation, BedrockParticleOptions>()

    fun loadEffects(resourceManager: ResourceManager) {
        LOGGER.info("Loading particle effects...")
        effects.clear()
        resourceManager.listResources("bedrock/particles") { path -> path.endsWith(".particle.json") }.forEach { identifier, resource ->
            try {
                resource.open().use { stream ->
                    val json = String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    val effect = SnowstormParticleReader.loadEffect(GSON.fromJson(json, JsonObject::class.java))
                    effects[effect.id] = effect
                }
            }
            catch (e: Exception) {
                LOGGER.error(e)
            }
        }

        LOGGER.info("Loaded ${effects.size} particle effects")

        loadLightConfig(resourceManager)
    }

    /**
     * Loads per-particle dynamic-light levels from a separate Cobblemon data location and merges them
     * onto the already-loaded effects. This is kept OUT of the `.particle.json` on purpose: Snowstorm
     * (the Bedrock particle editor) rebuilds the components block on export and silently drops unknown
     * components, so a light field inside the particle file would be lost on re-save. A sidecar file is
     * never touched by Snowstorm.
     *
     * Files live under `assets/<namespace>/particle_light/` (any `.json` file), each a map of
     * particle-effect id to its light config, e.g.:
     * ```json
     * {
     *   "cobblemon:thunderbolt_actor": { "light_level": 14 },
     *   "cobblemon:flamethrower_actor": { "light_levels": [0, 8, 14, 14, 8] }
     * }
     * ```
     * `light_level` is a flat 0..15 level every particle emits; the optional `light_levels` array gives
     * a level per sprite-sheet frame (overrides the flat level). Entries for unknown effect ids are
     * ignored.
     */
    private fun loadLightConfig(resourceManager: ResourceManager) {
        // LambDynamicLights is the only thing that consumes these levels. If it isn't installed, don't
        // even read the sidecar files — the light config is never parsed, applied, or considered.
        if (!Cobblemon.implementation.isModInstalled("lambdynlights")) return

        var applied = 0
        resourceManager.listResources("particle_light") { path -> path.endsWith(".json") }.forEach { identifier, resource ->
            try {
                resource.open().use { stream ->
                    val json = String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    val root = GSON.fromJson(json, JsonObject::class.java)
                    for ((key, value) in root.entrySet()) {
                        val effect = effects[ResourceLocation.parse(key)] ?: continue
                        val config = value.asJsonObject
                        config.get("light_level")?.asInt?.let { effect.particle.lightEmission = it.coerceIn(0, 15) }
                        config.getAsJsonArray("light_levels")?.let { arr ->
                            effect.particle.lightEmissionFrames = arr.map { it.asInt.coerceIn(0, 15) }
                        }
                        applied++
                    }
                }
            } catch (e: Exception) {
                LOGGER.error("Failed to load particle light config $identifier", e)
            }
        }
        if (applied > 0) LOGGER.info("Applied dynamic-light config to $applied particle effect(s)")
    }

    fun getEffect(identifier: ResourceLocation): BedrockParticleOptions? = effects[identifier]
}