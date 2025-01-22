/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.models.blockbench.repository

import com.cobblemon.mod.common.api.data.DataRegistry
import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.cobblemon.mod.common.client.render.models.blockbench.TexturedModel
import com.cobblemon.mod.common.client.render.models.blockbench.egg.EggModel
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.reflect.TypeToken
import net.minecraft.server.packs.PackType
import net.minecraft.server.level.ServerPlayer
import net.minecraft.resources.ResourceLocation

/**
 *  A repo to load our ONE egg model lmao
 */
object EggModelRepo : VaryingModelRepository<EggModel>() {
    override val poserClass = EggModel::class.java
    override val title = "Egg Model"
    override val type = "egg"
    override val variationDirectories: List<String> = emptyList()
    override val poserDirectories: List<String> = emptyList()
    override val modelDirectories: List<String> = listOf("bedrock/egg_models")
    override val animationDirectories: List<String> = listOf("bedrock/egg_animations")
    override val fallback = cobblemonResource("egg")
    override val isForLivingEntityRenderer = false

    lateinit var eggModel: EggModel

    override fun registerInBuiltPosers() {}
}


