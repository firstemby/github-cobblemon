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
import java.util.HashMap
import java.util.function.Function

object SpawningContextMoLangFunctions {
    val holder = mutableListOf<(SpawnablePosition) -> HashMap<String, (MoParams) -> Any>>(
        { spawningContext ->
            val map = hashMapOf<String, (MoParams) -> Any>()
            val worldValue = spawningContext.world.registryAccess().registryOrThrow(Registries.DIMENSION).wrapAsHolder(spawningContext.world).asWorldMoLangValue()
            val biomeValue = spawningContext.biomeHolder.asBiomeMoLangValue()
            map.put("biome") { _ -> biomeValue }
            map.put("world") { _ -> worldValue }
            map.put("light") { _ -> DoubleValue(spawningContext.light.toDouble()) }
            map.put("x") { _ -> DoubleValue(spawningContext.position.x.toDouble()) }
            map.put("y") { _ -> DoubleValue(spawningContext.position.y.toDouble()) }
            map.put("z") { _ -> DoubleValue(spawningContext.position.z.toDouble()) }
            map.put("moon_phase") { _ -> DoubleValue(spawningContext.moonPhase.toDouble()) }
            map.put("can_see_sky") { _ -> DoubleValue(spawningContext.canSeeSky) }
            map.put("sky_light") { _ -> DoubleValue(spawningContext.skyLight.toDouble()) }
            map.put("player") { _ ->
                val causeEntity = spawningContext.cause.entity ?: return@put DoubleValue.ZERO
                if (causeEntity is ServerPlayer) {
                    return@put causeEntity.asMoLangValue()
                } else {
                    return@put DoubleValue.ZERO
                }
            }
            map
        }
    )
}
