/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.battle

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.battle.ClientBattleInformationRepository
import com.cobblemon.mod.common.client.particle.BedrockParticleOptionsRepository
import com.cobblemon.mod.common.client.particle.ParticleStorm
import com.cobblemon.mod.common.client.render.MatrixWrapper
import com.cobblemon.mod.common.net.messages.client.battle.BattleInformationPacket
import com.cobblemon.mod.common.net.messages.client.battle.FieldEffect
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.authlib.minecraft.client.MinecraftClient
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation

object BattleInformationHandler : ClientNetworkPacketHandler<BattleInformationPacket> {
    override fun handle(packet: BattleInformationPacket, client: Minecraft) {
        val currentWeather: FieldEffect? = getCurrentWeather()
        ClientBattleInformationRepository.battles[packet.battle] = packet.informationDTO
        val newWeather: FieldEffect? = packet.informationDTO.weather
        if (newWeather != null && currentWeather?.id != newWeather.id) {
            client.executeIfPossible { playWeatherParticle(newWeather, client) }
        }
    }

    private fun getCurrentWeather(): FieldEffect? {
        val currentBattleId = CobblemonClient.battle?.battleId ?: return null
        val currentBattleInfo = ClientBattleInformationRepository.battles[currentBattleId] ?: return null
        return currentBattleInfo.weather
    }

    private fun playWeatherParticle(weather: FieldEffect, client: Minecraft) {
        if (weather.sourceEntityId == null) return
        val source = client.level?.getEntity(weather.sourceEntityId) ?: return
        val particle = getParticleForWeather(weather) ?: return
        val wrapper = MatrixWrapper()
        val matrix = PoseStack()
        wrapper.updateMatrix(matrix.last().pose())
        wrapper.updatePosition(source.position())
        val world = Minecraft.getInstance().level ?: return
        val effect = BedrockParticleOptionsRepository.getEffect(particle) ?: return
        val runtime = MoLangRuntime()
        runtime.environment.query.addFunction("is_weather_active") { DoubleValue(getCurrentWeather()?.id == weather.id) }
        ParticleStorm(
            effect,
            wrapper,
            wrapper,
            world,
            runtime = runtime
        ).spawn()
    }

    fun getParticleForWeather(weather: FieldEffect): ResourceLocation? {
        return when (weather.id) {
            "hail" -> cobblemonResource("battle_hail")
            "snow" -> cobblemonResource("battle_snow")
            "raindance" -> cobblemonResource("battle_rain")
            "sandstorm" -> cobblemonResource("battle_sandstorm")
            else -> null
        }
    }
}