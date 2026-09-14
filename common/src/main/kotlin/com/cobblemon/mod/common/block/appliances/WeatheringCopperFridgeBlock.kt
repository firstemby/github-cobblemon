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
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
import java.util.function.BiFunction
import java.util.function.Function

//I just cloned WeatheringCopperFullBlock.class into here the best I could, this likely needs a *lot* of clean up, plus a bit of code from WeatheringCopperDoorBlock.class to get it to sync, hopefully
class WeatheringCopperFridgeBlock(weatherState: WeatheringCopper.WeatherState, settings: Properties) : CopperFridgeBlock(settings), WeatheringCopper {
    val CODEC: MapCodec<WeatheringCopperFridgeBlock?> =
        RecordCodecBuilder.mapCodec<WeatheringCopperFridgeBlock?>(Function { instance: RecordCodecBuilder.Instance<WeatheringCopperFridgeBlock?>? ->
            instance!!.group(
                WeatheringCopper.WeatherState.CODEC.fieldOf("weathering_state")
                    .forGetter<WeatheringCopperFridgeBlock?> { obj: WeatheringCopperFridgeBlock? -> obj!!.getAge() },
                propertiesCodec<WeatheringCopperFridgeBlock?>()
            ).apply<WeatheringCopperFridgeBlock?>(
                instance,
                BiFunction { weatherState: WeatheringCopper.WeatherState?, properties: Properties? ->
                    WeatheringCopperFullBlock(
                        weatherState,
                        properties
                    )
                })
        })
    private var weatherState: WeatheringCopper.WeatherState? = null

    override fun codec(): MapCodec<WeatheringCopperFridgeBlock?> {
        return CODEC
    }

    fun WeatheringCopperFridgeBlock(weatherState: WeatheringCopper.WeatherState, properties: Properties) {
        super(properties)
        this.weatherState = weatherState
    }

    override fun randomTick(state: BlockState, level: ServerLevel?, pos: BlockPos?, random: RandomSource?) {
        if (state.getValue<DoubleBlockHalf?>(HALF) == DoubleBlockHalf.LOWER) {
            this.changeOverTime(state, level, pos, random)
        }
    }

    override fun isRandomlyTicking(state: BlockState): Boolean {
        return WeatheringCopper.getNext(state.getBlock()).isPresent()
    }

    override fun getAge(): WeatheringCopper.WeatherState {
        return this.weatherState!!
    }
}