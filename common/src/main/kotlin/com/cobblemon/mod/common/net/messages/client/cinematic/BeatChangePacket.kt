/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.cinematic

import com.cobblemon.mod.common.api.cinematic.model.FeedDeclaration
import com.cobblemon.mod.common.api.camera.model.HudElement
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import java.util.UUID

class BeatChangePacket(
    val sceneId: UUID,
    val beatId: String,
    val instantTransition: Boolean,
    val renderPlayerBody: Boolean,
    val hiddenHudElements: Set<HudElement>,
    val activeFeedIds: Set<String> = emptySet()
) : NetworkPacket<BeatChangePacket> {

    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(sceneId)
        buffer.writeUtf(beatId)
        buffer.writeBoolean(instantTransition)
        buffer.writeBoolean(renderPlayerBody)
        buffer.writeVarInt(encodeHudBitfield(hiddenHudElements))
        buffer.writeVarInt(activeFeedIds.size)
        activeFeedIds.forEach { buffer.writeUtf(it) }
    }

    companion object {
        val ID = cobblemonResource("beat_change")

        fun decode(buffer: RegistryFriendlyByteBuf): BeatChangePacket {
            val sceneId = buffer.readUUID()
            val beatId = buffer.readUtf()
            val instant = buffer.readBoolean()
            val renderBody = buffer.readBoolean()
            val hidden = decodeHudBitfield(buffer.readVarInt())
            val feedCount = buffer.readVarInt()
            require(feedCount in 0..FeedDeclaration.MAX_FEEDS) { "feed count $feedCount exceeds ${FeedDeclaration.MAX_FEEDS}" }
            val activeFeedIds = mutableSetOf<String>()
            repeat(feedCount) { activeFeedIds.add(buffer.readUtf()) }
            return BeatChangePacket(sceneId, beatId, instant, renderBody, hidden, activeFeedIds)
        }

        private fun encodeHudBitfield(hidden: Set<HudElement>): Int {
            var bits = 0
            HudElement.entries.forEachIndexed { index, element ->
                if (element in hidden) bits = bits or (1 shl index)
            }
            return bits
        }

        private fun decodeHudBitfield(bits: Int): Set<HudElement> {
            val result = mutableSetOf<HudElement>()
            HudElement.entries.forEachIndexed { index, element ->
                if ((bits shr index) and 1 == 1) result.add(element)
            }
            return result
        }
    }
}
