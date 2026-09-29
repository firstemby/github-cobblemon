/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.cinematic

import com.cobblemon.mod.common.api.cinematic.model.FeedDeclaration
import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.api.cinematic.model.sources.Lens
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import java.util.UUID

class FeedTransformPacket(
    val sceneId: UUID,
    val feeds: List<FeedTransform>
) : NetworkPacket<FeedTransformPacket> {

    override val id: ResourceLocation = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(sceneId)
        buffer.writeVarInt(feeds.size)
        for (feed in feeds) {
            buffer.writeUtf(feed.feedId)
            buffer.writeDouble(feed.position.x)
            buffer.writeDouble(feed.position.y)
            buffer.writeDouble(feed.position.z)
            buffer.writeBoolean(feed.rotation != null)
            feed.rotation?.let {
                buffer.writeFloat(it.yaw)
                buffer.writeFloat(it.pitch)
            }
            buffer.writeBoolean(feed.fov != null)
            feed.fov?.let { buffer.writeFloat(it) }
            buffer.writeBoolean(feed.roll != null)
            feed.roll?.let { buffer.writeFloat(it) }
            buffer.writeBoolean(feed.boneHint != null)
            feed.boneHint?.let {
                buffer.writeVarInt(it.entityId)
                buffer.writeUtf(it.boneName)
                buffer.writeDouble(it.offset.x)
                buffer.writeDouble(it.offset.y)
                buffer.writeDouble(it.offset.z)
                buffer.writeBoolean(it.smooth)
                buffer.writeFloat(it.yawOffset)
                buffer.writeFloat(it.pitchOffset)
            }
            buffer.writeBoolean(feed.lens != null)
            feed.lens?.let {
                buffer.writeBoolean(it.aspectCrop != null)
                it.aspectCrop?.let(buffer::writeFloat)
                buffer.writeFloat(it.letterbox)
            }
        }
    }

    companion object {
        val ID = cobblemonResource("feed_transform")

        fun decode(buffer: RegistryFriendlyByteBuf): FeedTransformPacket {
            val sceneId = buffer.readUUID()
            val count = buffer.readVarInt()
            require(count in 0..FeedDeclaration.MAX_FEEDS) { "feed count $count exceeds ${FeedDeclaration.MAX_FEEDS}" }
            val feeds = ArrayList<FeedTransform>(count)
            repeat(count) {
                val feedId = buffer.readUtf()
                val position = Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())
                val rotation = if (buffer.readBoolean()) CameraRotation(buffer.readFloat(), buffer.readFloat()) else null
                val fov = if (buffer.readBoolean()) buffer.readFloat() else null
                val roll = if (buffer.readBoolean()) buffer.readFloat() else null
                val boneHint = if (buffer.readBoolean()) FeedBoneHint(
                    entityId = buffer.readVarInt(),
                    boneName = buffer.readUtf(),
                    offset = Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                    smooth = buffer.readBoolean(),
                    yawOffset = buffer.readFloat(),
                    pitchOffset = buffer.readFloat()
                ) else null
                val lens = if (buffer.readBoolean()) Lens(
                    aspectCrop = if (buffer.readBoolean()) buffer.readFloat() else null,
                    letterbox = buffer.readFloat()
                ) else null
                feeds.add(FeedTransform(feedId, position, rotation, fov, roll, boneHint, lens))
            }
            return FeedTransformPacket(sceneId, feeds)
        }
    }
}

data class FeedTransform(
    val feedId: String,
    val position: Vec3,
    val rotation: CameraRotation? = null,
    val fov: Float? = null,
    val roll: Float? = null,
    val boneHint: FeedBoneHint? = null,
    val lens: Lens? = null
) {
    init {
        require(feedId.isNotBlank()) { "Feed transform id must be non-blank" }
        require(listOf(position.x, position.y, position.z).all(Double::isFinite)) { "Feed position must be finite" }
        require(rotation?.let { it.yaw.isFinite() && it.pitch.isFinite() } != false) { "Feed rotation must be finite" }
        require(fov == null || fov in 1f..179f) { "Feed FOV must be in [1, 179]" }
        require(roll?.isFinite() != false) { "Feed roll must be finite" }
    }
}

data class FeedBoneHint(
    val entityId: Int,
    val boneName: String,
    val offset: Vec3,
    val smooth: Boolean,
    val yawOffset: Float = 0f,
    val pitchOffset: Float = 0f
) {
    init {
        require(boneName.isNotBlank()) { "Feed bone name must be non-blank" }
        require(listOf(offset.x, offset.y, offset.z).all(Double::isFinite)) { "Feed bone offset must be finite" }
        require(yawOffset.isFinite() && pitchOffset.isFinite()) { "Feed bone rotation offsets must be finite" }
    }
}
