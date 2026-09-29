/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.cinematic

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import java.util.UUID

class FeedAssignmentPacket(
    val sceneId: UUID,
    val feedId: String,
    val shotId: String,
    val transitionType: TransitionType,
    val transitionDurationSeconds: Float
) : NetworkPacket<FeedAssignmentPacket> {

    init {
        require(feedId.isNotBlank()) { "feed id must be non-blank" }
        require(shotId.isNotBlank()) { "shot id must be non-blank" }
        require(transitionDurationSeconds.isFinite() && transitionDurationSeconds >= 0f) {
            "transition duration must be finite and non-negative"
        }
    }

    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(sceneId)
        buffer.writeUtf(feedId)
        buffer.writeUtf(shotId)
        buffer.writeByte(transitionType.ordinal)
        buffer.writeFloat(transitionDurationSeconds)
    }

    enum class TransitionType { INSTANT, BLEND }

    companion object {
        val ID = cobblemonResource("feed_assignment")

        fun decode(buffer: RegistryFriendlyByteBuf): FeedAssignmentPacket {
            val sceneId = buffer.readUUID()
            val feedId = buffer.readUtf()
            val shotId = buffer.readUtf()
            val ordinal = buffer.readByte().toInt()
            val type = requireNotNull(TransitionType.entries.getOrNull(ordinal)) {
                "Unknown feed assignment transition type: $ordinal"
            }
            val duration = buffer.readFloat()
            return FeedAssignmentPacket(sceneId, feedId, shotId, type, duration)
        }
    }
}
