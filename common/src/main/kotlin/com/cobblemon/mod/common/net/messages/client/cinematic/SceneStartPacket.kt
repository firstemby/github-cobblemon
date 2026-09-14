/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.cinematic

import com.cobblemon.mod.common.api.cinematic.model.FeedDeclaration
import com.cobblemon.mod.common.api.cinematic.model.FeedKind
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import java.util.UUID

/** sceneId is the running session UUID, not the authored scene definition ID. */
class SceneStartPacket(
    val sceneId: UUID,
    val sceneDefinitionId: String,
    val origin: Vec3,
    val feeds: List<FeedDeclaration> = emptyList()
) : NetworkPacket<SceneStartPacket> {

    init {
        require(sceneDefinitionId.isNotBlank()) { "scene definition id must be non-blank" }
        require(origin.x.isFinite() && origin.y.isFinite() && origin.z.isFinite()) { "origin must be finite" }
        require(feeds.size <= FeedDeclaration.MAX_FEEDS) { "feed count ${feeds.size} exceeds ${FeedDeclaration.MAX_FEEDS}" }
        require(feeds.mapTo(mutableSetOf()) { it.id }.size == feeds.size) { "feed ids must be unique" }
    }

    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(sceneId)
        buffer.writeUtf(sceneDefinitionId)
        buffer.writeDouble(origin.x)
        buffer.writeDouble(origin.y)
        buffer.writeDouble(origin.z)
        buffer.writeVarInt(feeds.size)
        feeds.forEach { feed ->
            buffer.writeUtf(feed.id)
            encodeFeedKind(buffer, feed.kind)
        }
    }

    companion object {
        val ID = cobblemonResource("scene_start")

        fun decode(buffer: RegistryFriendlyByteBuf): SceneStartPacket {
            val sceneId = buffer.readUUID()
            val definitionId = buffer.readUtf()
            val origin = Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())
            val feedCount = buffer.readVarInt()
            require(feedCount in 0..FeedDeclaration.MAX_FEEDS) { "feed count $feedCount exceeds ${FeedDeclaration.MAX_FEEDS}" }
            val feeds = List(feedCount) {
                FeedDeclaration(
                    id = buffer.readUtf(),
                    kind = decodeFeedKind(buffer)
                )
            }
            return SceneStartPacket(sceneId, definitionId, origin, feeds)
        }

        private const val KIND_FULLSCREEN: Byte = 0
        private const val KIND_PIP: Byte = 1
        private const val KIND_SPLIT: Byte = 2
        private const val KIND_DIAGONAL: Byte = 3

        private fun encodeFeedKind(buffer: RegistryFriendlyByteBuf, kind: FeedKind) {
            when (kind) {
                is FeedKind.Fullscreen -> buffer.writeByte(KIND_FULLSCREEN.toInt())
                is FeedKind.Pip -> {
                    buffer.writeByte(KIND_PIP.toInt())
                    buffer.writeByte(kind.corner.ordinal)
                    buffer.writeFloat(kind.size)
                }
                is FeedKind.Split -> {
                    buffer.writeByte(KIND_SPLIT.toInt())
                    buffer.writeByte(kind.side.ordinal)
                    buffer.writeFloat(kind.size)
                }
                is FeedKind.Diagonal -> {
                    buffer.writeByte(KIND_DIAGONAL.toInt())
                    buffer.writeByte(kind.half.ordinal)
                }
            }
        }

        private fun decodeFeedKind(buffer: RegistryFriendlyByteBuf): FeedKind {
            return when (val tag = buffer.readByte()) {
                KIND_FULLSCREEN -> FeedKind.Fullscreen
                KIND_PIP -> FeedKind.Pip(
                    corner = FeedKind.Corner.entries[buffer.readByte().toInt()],
                    size = buffer.readFloat()
                )
                KIND_SPLIT -> FeedKind.Split(
                    side = FeedKind.Side.entries[buffer.readByte().toInt()],
                    size = buffer.readFloat()
                )
                KIND_DIAGONAL -> FeedKind.Diagonal(
                    half = FeedKind.DiagonalHalf.entries[buffer.readByte().toInt()]
                )
                else -> throw IllegalArgumentException("Unknown feed kind tag: $tag")
            }
        }
    }
}
