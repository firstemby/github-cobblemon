/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common

import com.cobblemon.mod.common.entity.sensor.BattlingPokemonSensor
import com.cobblemon.mod.common.entity.sensor.MateSensor
import com.cobblemon.mod.common.entity.sensor.NPCBattlingSensor
import com.cobblemon.mod.common.entity.sensor.NestSensor
import java.util.function.Supplier
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ai.sensing.Sensor
import net.minecraft.world.entity.ai.sensing.SensorType

object CobblemonSensors {
    val sensors = mutableMapOf<String, SensorType<*>>()

    val NPC_BATTLING = register("npc_battling", ::NPCBattlingSensor)
    val BATTLING_POKEMON = register("battling_pokemon", ::BattlingPokemonSensor)
    val MATE_SENSOR = register("mate_sensor", ::MateSensor)
    val NEST_SENSOR = register("nest_sensor", ::NestSensor)

    fun <E : Entity, U : Sensor<E>> register(id: String, supplier: Supplier<U>): SensorType<U> {
        val sensor = SensorType(supplier)
        sensors[id] = sensor
        return sensor
    }
}