/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.riding.behaviour

import com.cobblemon.mod.common.api.net.Decodable
import com.cobblemon.mod.common.api.net.Encodable
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.ifClient
import com.cobblemon.mod.common.util.ifServer
import net.minecraft.client.player.RemotePlayer
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import kotlin.reflect.KProperty

/**
 * Represents the state of a Pokemon when being ridden.
 * This is intended to contain mutable state that is passed between the client and server
 * or temporary state that is used during the riding process.
 *
 * This will be destroyed when the a user dismounts the Pokemon.
 *
 * @author landonjw
 */
interface RidingBehaviourState : Encodable, Decodable {
    fun reset()
    fun copy(): RidingBehaviourState
    fun shouldSync(previous: RidingBehaviourState): Boolean
}

object NoState : RidingBehaviourState {
    override fun encode(buffer: RegistryFriendlyByteBuf) = Unit
    override fun decode(buffer: RegistryFriendlyByteBuf) = Unit
    override fun copy() = NoState
    override fun reset() = Unit
    override fun shouldSync(previous: RidingBehaviourState) = false
}

fun <T> ridingState(value: T, side: Side) = SidedRidingState(value, side)

class SidedRidingState<T>(private var value: T, val side: Side) {
    fun get() = value

    fun set(value: T, forced: Boolean = false) {
        if (forced) {
            this.value = value
        }
        else {
            when (side) {
                Side.BOTH -> { this.value = value }
                Side.CLIENT -> { ifClient { this.value = value } }
                Side.SERVER -> { ifServer { this.value = value } }
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other is SidedRidingState<*>) {
            return value == other.value
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}

enum class Side {
    SERVER, CLIENT, BOTH
}

enum class DriverSide {
    LOCAL, REMOTE, BOTH
}
