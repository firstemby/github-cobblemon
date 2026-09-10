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
        val worldValue =
            spawningContext
                .world
                .registryAccess()
                .registryOrThrow(Registries.DIMENSION)
                .wrapAsHolder(spawningContext.world)
                .asWorldMoLangValue()
        val biomeValue =
            spawningContext
                .biomeHolder
                .asBiomeMoLangValue()
        val functions = hashMapOf<String, (MoParams) -> Any>()

        functions["biome"] = { biomeValue }
        functions["world"] = { worldValue }
        functions["light"] = { DoubleValue(spawningContext.light.toDouble()) }
        functions["x"] = { DoubleValue(spawningContext.position.x.toDouble()) }
        functions["y"] = { DoubleValue(spawningContext.position.y.toDouble()) }
        functions["z"] = { DoubleValue(spawningContext.position.z.toDouble()) }
        functions["moon_phase"] = { DoubleValue(spawningContext.moonPhase.toDouble()) }
        functions["can_see_sky"] = { DoubleValue(spawningContext.canSeeSky) }
        functions["sky_light"] = { DoubleValue(spawningContext.skyLight.toDouble()) }
        functions["player"] = player@{
            val causeEntity = spawningContext.cause.entity ?: return@player DoubleValue.ZERO
            if (causeEntity is ServerPlayer) return@player causeEntity.asMoLangValue()
            DoubleValue.ZERO
        }

        return functions
    }
}
