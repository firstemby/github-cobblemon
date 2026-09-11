/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battle

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.snowstorm.ExpressionEmitterLifetime
import com.cobblemon.mod.common.api.snowstorm.BedrockParticleOptions
import com.cobblemon.mod.common.api.snowstorm.ParticleEmitterAction
import com.cobblemon.mod.common.particle.SnowstormParticleReader
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WeatherParticleResourcesTest {
    init {
        // Initialize the particle registries before the reader constructs their concrete subtypes.
        BedrockParticleOptions.CODEC
    }

    private val names = listOf(
        "battle_hail", "battle_hail_pieces", "battle_rain", "battle_splash",
        "battle_sandstorm", "battle_sandstorm_dust", "battle_sandstorm_obscure", "battle_snow"
    )

    private fun loadEffects() = names.map { name ->
        val path = "assets/cobblemon/bedrock/particles/weather/$name.particle.json"
        val resource = requireNotNull(javaClass.classLoader.getResource(path)) { "Missing $path" }
        resource.openStream().bufferedReader().use {
            SnowstormParticleReader.loadEffect(JsonParser.parseReader(it).asJsonObject)
        }
    }

    @Test
    fun `weather effects load with all textures and child effects packaged`() {
        val effects = loadEffects()
        val ids = effects.map { it.id }.toSet()
        assertEquals(names.size, ids.size)
        effects.forEach { effect ->
            val texture = effect.particle.texture
            assertNotNull(javaClass.classLoader.getResource("assets/${texture.namespace}/textures/particle/${texture.path}.png"), "Missing texture for ${effect.id}")
            effect.events.values.mapNotNull { it.particleEffect }.forEach { child ->
                assertTrue(child.effect in ids, "Missing child effect ${child.effect}")
            }
        }
    }

    @Test
    fun `weather Molang stops every continuous emitter when the weather ends`() {
        val continuous = loadEffects().filter { it.emitter.lifetime is ExpressionEmitterLifetime }
        assertEquals(6, continuous.size)
        var weatherActive = true
        val runtime = MoLangRuntime()
        runtime.environment.query.addFunction("is_weather_active") { DoubleValue(weatherActive) }
        continuous.forEach {
            assertEquals(ParticleEmitterAction.GO, it.emitter.lifetime.getAction(runtime, true, 1.0), it.id.toString())
        }
        weatherActive = false
        continuous.forEach {
            assertEquals(ParticleEmitterAction.STOP, it.emitter.lifetime.getAction(runtime, true, 1.0), it.id.toString())
        }
    }
}
