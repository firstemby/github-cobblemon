/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.pokemon.update

import com.cobblemon.mod.common.api.riding.RidingStyle
import com.cobblemon.mod.common.api.riding.stats.RidingStat
import com.cobblemon.mod.common.net.IntSize
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readEnumConstant
import com.cobblemon.mod.common.util.readSizedInt
import net.minecraft.network.RegistryFriendlyByteBuf

class RideBoostUpdatePacket(pokemon: () -> Pokemon, rideStyle: RidingStyle, rideStat: RidingStat, value: Int)
    : IntUpdatePacket<RideBoostUpdatePacket>(pokemon, value) {
    override val id = ID
    override fun getSize() = IntSize.U_BYTE
    private val rideStyle = rideStyle
    private val rideStat = rideStat

    override fun set(pokemon: Pokemon, value: Int) {
        pokemon.setRideBoost(rideStyle, rideStat, value)
    }

    companion object {
        val ID = cobblemonResource("ride_boost_update")

        fun decode(buffer: RegistryFriendlyByteBuf): RideBoostUpdatePacket {
            val pokemon = decodePokemon(buffer)
            val rideStyle = buffer.readEnumConstant(RidingStyle::class.java)
            val rideStat = buffer.readEnumConstant(RidingStat::class.java)
            val value = buffer.readSizedInt(IntSize.U_SHORT)
            return RideBoostUpdatePacket(pokemon, rideStyle, rideStat, value)
        }
    }
}