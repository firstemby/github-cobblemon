/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molang.function

import com.bedrockk.molang.runtime.MoParams
import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asBiomeMoLangValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMoLangValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asWorldMoLangValue
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerPlayer

object SpawnablePositionMoLangFunctions : AbstractMoLangFunctionHolder<SpawnablePosition>() {
    override fun SpawnablePosition.moLangFunctions(): MutableMap<String, (MoParams) -> Any> {
        val spawningContext = this
        val map = mutableMapOf<String, (MoParams) -> Any>()
        val worldValue = spawningContext.world.registryAccess().registryOrThrow(Registries.DIMENSION)
            .wrapAsHolder(spawningContext.world).asWorldMoLangValue()
        val biomeValue = spawningContext.biomeHolder.asBiomeMoLangValue()
        map["biome"] = { _ -> biomeValue }
        map["world"] = { _ -> worldValue }
        map["light"] = { _ -> DoubleValue(spawningContext.light.toDouble()) }
        map["x"] = { _ -> DoubleValue(spawningContext.position.x.toDouble()) }
        map["y"] = { _ -> DoubleValue(spawningContext.position.y.toDouble()) }
        map["z"] = { _ -> DoubleValue(spawningContext.position.z.toDouble()) }
        map["moon_phase"] = { _ -> DoubleValue(spawningContext.moonPhase.toDouble()) }
        map["can_see_sky"] = { _ -> DoubleValue(spawningContext.canSeeSky) }
        map["sky_light"] = { _ -> DoubleValue(spawningContext.skyLight.toDouble()) }
        map["player"] = put@{ _ ->
            val causeEntity = spawningContext.cause.entity ?: return@put DoubleValue.ZERO
            if (causeEntity is ServerPlayer) {
                return@put causeEntity.asMoLangValue()
            } else {
                return@put DoubleValue.ZERO
            }
        }
        return map
    }
}
