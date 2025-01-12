/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.CobblemonNetwork.sendPacket
import com.cobblemon.mod.common.api.ai.BrainPreset
import com.cobblemon.mod.common.api.ai.config.BrainConfig
import com.cobblemon.mod.common.api.ai.config.task.TaskConfig
import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.net.messages.client.data.BrainPresetSyncPacket
import com.cobblemon.mod.common.util.adapters.ActivityAdapter
import com.cobblemon.mod.common.util.adapters.BrainConfigAdapter
import com.cobblemon.mod.common.util.adapters.ExpressionAdapter
import com.cobblemon.mod.common.util.adapters.ExpressionLikeAdapter
import com.cobblemon.mod.common.util.adapters.ExpressionOrEntityVariableAdapter
import com.cobblemon.mod.common.util.adapters.IdentifierAdapter
import com.cobblemon.mod.common.util.adapters.TaskConfigAdapter
import com.cobblemon.mod.common.util.adapters.TranslatedTextAdapter
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.mojang.datafixers.util.Either
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.world.entity.schedule.Activity

object CobblemonBrainConfigs : JsonDataRegistry<BrainPreset> {
    override val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(Activity::class.java, ActivityAdapter)
        .registerTypeAdapter(Expression::class.java, ExpressionAdapter)
        .registerTypeAdapter(ExpressionLike::class.java, ExpressionLikeAdapter)
        .registerTypeAdapter(BrainConfig::class.java, BrainConfigAdapter)
        .registerTypeAdapter(TaskConfig::class.java, TaskConfigAdapter)
        .registerTypeAdapter(
            TypeToken.getParameterized(Either::class.java, Expression::class.java, MoLangConfigVariable::class.java).type,
            ExpressionOrEntityVariableAdapter
        )
        .registerTypeAdapter(Component::class.java, TranslatedTextAdapter)
        .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
        .create()

    override val typeToken = TypeToken.get(BrainPreset::class.java)
    override val resourcePath = "brain_presets"
    override val id: ResourceLocation = cobblemonResource("brain_presets")
    override val type = PackType.SERVER_DATA
    override val observable = SimpleObservable<CobblemonBrainConfigs>()

    val presets = mutableMapOf<ResourceLocation, BrainPreset>()

    override fun sync(player: ServerPlayer) {
        player.sendPacket(BrainPresetSyncPacket(presets))
    }

    override fun reload(data: Map<ResourceLocation, BrainPreset>) {
        presets.clear()
        presets.putAll(data)
    }
}