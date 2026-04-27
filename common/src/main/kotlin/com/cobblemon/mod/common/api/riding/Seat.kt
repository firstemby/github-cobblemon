/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.riding

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.api.net.Encodable
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.getString
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.writeString
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * Seat Properties are responsible for the base information that would then be used to construct a Seat on an entity.
 */
data class Seat(
    val locator: String = "",
    val condition: Expression?,
    val poseAnimations: MutableList<SeatPoseAnimations>?,
    val handsBusy: Boolean = false,
) : Encodable {

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeNullable(locator) { _, v -> buffer.writeString(v) }
        buffer.writeNullable(condition) { _, v -> buffer.writeString(v.getString()) }
        buffer.writeNullable(poseAnimations) { _, v ->
            buffer.writeCollection(v) { _, animations -> animations.encode(buffer) }
        }
        buffer.writeBoolean(handsBusy)
    }

    companion object {
        fun decode(buffer: RegistryFriendlyByteBuf) : Seat {
            return Seat(
                buffer.readNullable { buffer.readString() } ?: "",
                buffer.readNullable { buffer.readString().asExpression() },
                buffer.readNullable { buffer.readList { SeatPoseAnimations.decode(buffer) } },
                buffer.readBoolean()
            )
        }
    }
}

class SeatPoseAnimations {
    val poseTypes = mutableSetOf<PoseType>()
    var animations = mutableListOf<RidingAnimation>()

    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeCollection(poseTypes) { _, poseType -> buffer.writeString(poseType.name) }
        buffer.writeCollection(animations) { _, animation -> animation.encode(buffer) }
    }

    companion object {
        fun decode(buffer: RegistryFriendlyByteBuf) : SeatPoseAnimations {
            val animations = SeatPoseAnimations()
            animations.poseTypes.addAll(buffer.readList { buffer.readString().let { PoseType.valueOf(it) } })
            animations.animations = buffer.readList { RidingAnimation.decode(buffer) }
            return animations
        }
    }
}
