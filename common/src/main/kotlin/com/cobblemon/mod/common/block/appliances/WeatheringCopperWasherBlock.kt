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
class WeatheringCopperWasherBlock(weatherState: WeatheringCopper.WeatherState, settings: Properties) : CopperWasherBlock(settings), WeatheringCopper {
    val CODEC: MapCodec<WeatheringCopperWasherBlock?> =
        RecordCodecBuilder.mapCodec<WeatheringCopperWasherBlock?>(Function { instance: RecordCodecBuilder.Instance<WeatheringCopperWasherBlock?>? ->
            instance!!.group(
                WeatheringCopper.WeatherState.CODEC.fieldOf("weathering_state")
                    .forGetter<WeatheringCopperWasherBlock?> { obj: WeatheringCopperWasherBlock? -> obj!!.getAge() },
                propertiesCodec<WeatheringCopperWasherBlock?>()
            ).apply<WeatheringCopperWasherBlock?>(
                instance,
                BiFunction { weatherState: WeatheringCopper.WeatherState?, properties: Properties? ->
                    WeatheringCopperFullBlock(
                        weatherState,
                        properties
                    )
                })
        })
    private var weatherState: WeatheringCopper.WeatherState? = null

    override fun codec(): MapCodec<WeatheringCopperWasherBlock?> {
        return CODEC
    }

    fun WeatheringCopperWasherBlock(weatherState: WeatheringCopper.WeatherState, properties: Properties) {
        super(properties)
        this.weatherState = weatherState
    }

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