/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.appliances

import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.WeatheringCopper
import net.minecraft.world.level.block.state.BlockState
import java.util.function.Function

//I just cloned WeatheringCopperBulbBlock.class into here the best I could, this likely needs a *lot* of clean up
class WeatheringCopperWasherBlock(val weatherState: WeatheringCopper.WeatherState, properties: Properties) :
    CopperWasherBlock(properties), WeatheringCopper {

    override fun randomTick(state: BlockState?, level: ServerLevel?, pos: BlockPos?, random: RandomSource?) {
        this.changeOverTime(state, level, pos, random)
    }

    override fun isRandomlyTicking(state: BlockState): Boolean {
        return WeatheringCopper.getNext(state.getBlock()).isPresent()
    }

    override fun getAge(): WeatheringCopper.WeatherState {
        return this.weatherState
    }

    companion object {
        val CODEC: MapCodec<WeatheringCopperWasherBlock> =
            RecordCodecBuilder.mapCodec(Function { instance: RecordCodecBuilder.Instance<WeatheringCopperWasherBlock>? ->
                instance!!.group(
                    WeatheringCopper.WeatherState.CODEC.fieldOf("weathering_state").forGetter({ obj: WeatheringCopperWasherBlock -> obj.getAge() }),
                    propertiesCodec()
                ).apply(
                    instance
                ) { weatherState: WeatheringCopper.WeatherState, properties: Properties -> WeatheringCopperWasherBlock(weatherState, properties)}
            })
    }

    override fun codec(): MapCodec<WeatheringCopperWasherBlock> = CODEC
}
