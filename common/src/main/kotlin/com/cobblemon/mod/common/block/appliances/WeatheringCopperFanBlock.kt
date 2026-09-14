/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.appliances

import com.cobblemon.mod.common.block.appliances.CopperOvenBlock
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.state.BlockState
import java.util.function.BiFunction
import java.util.function.Function

//I just cloned WeatheringCopperFullBlock.class into here the best I could, this likely needs a *lot* of clean up
class WeatheringCopperFanBlock(weatherState: WeatheringCopper.WeatherState, settings: Properties) : CopperFanBlock(settings), WeatheringCopper {
    /*val CODEC: MapCodec<WeatheringCopperFanBlock?> =
        RecordCodecBuilder.mapCodec<WeatheringCopperFanBlock?>(Function { instance: RecordCodecBuilder.Instance<WeatheringCopperFanBlock?>? ->
            instance!!.group(
                WeatheringCopper.WeatherState.CODEC.fieldOf("weathering_state")
                    .forGetter<WeatheringCopperFanBlock?> { obj: WeatheringCopperFanBlock? -> obj!!.getAge() },
                propertiesCodec<WeatheringCopperFanBlock?>()
            ).apply<WeatheringCopperFanBlock?>(
                instance,
                BiFunction { weatherState: WeatheringCopper.WeatherState?, properties: Properties? ->
                    WeatheringCopperFullBlock(
                        weatherState,
                        properties
                    )
                })
        })*/
    private var weatherState: WeatheringCopper.WeatherState? = null

    /*override fun codec(): MapCodec<WeatheringCopperFanBlock?> {
        return CODEC
    }*/

    override fun randomTick(state: BlockState?, level: ServerLevel?, pos: BlockPos?, random: RandomSource?) {
        this.changeOverTime(state, level, pos, random)
    }

    override fun isRandomlyTicking(state: BlockState): Boolean {
        return WeatheringCopper.getNext(state.getBlock()).isPresent()
    }

    override fun getAge(): WeatheringCopper.WeatherState {
        return this.weatherState!!
    }
}