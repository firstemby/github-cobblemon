/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.camera

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * Camera focus configuration packet.
 *
 * Sent when the camera's focus target changes (not every tick).
 * Uses a sealed class to represent different focus types in a type-safe manner.
 *
 * Focus types:
 * - None: No focus (free camera)
 * - Point: Static or dynamic point in space
 * - Entity: Entity center position
 * - Bone: Specific bone on entity's model (for animated tracking)
 */
class CameraFocusPacket(
    val cameraId: UUID,
    val focus: FocusData
) : NetworkPacket<CameraFocusPacket> {

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUUID(cameraId)

        when (focus) {
            is FocusData.None -> {
                buffer.writeByte(0)
            }
            is FocusData.Point -> {
                buffer.writeByte(1)
                buffer.writeDouble(focus.position.x)
                buffer.writeDouble(focus.position.y)
                buffer.writeDouble(focus.position.z)
            }
            is FocusData.Entity -> {
                buffer.writeByte(2)
                buffer.writeVarInt(focus.entityId)
            }
            is FocusData.Bone -> {
                buffer.writeByte(3)
                buffer.writeVarInt(focus.entityId)
                buffer.writeUtf(focus.boneName, 64)
                buffer.writeDouble(focus.offset.x)
                buffer.writeDouble(focus.offset.y)
                buffer.writeDouble(focus.offset.z)
                buffer.writeBoolean(focus.smooth)
            }
        }
    }

    /**
     * Sealed class representing different camera focus types.
     *
     * Each variant contains the data needed for that specific focus type.
     */
    sealed class FocusData {
        /**
         * No focus - free camera movement.
         */
        object None : FocusData()

        /**
         * Focus on a static point in space.
         *
         * @property position World-space position to focus on
         */
        data class Point(
            val position: Vec3
        ) : FocusData()

        /**
         * Focus on entity's center position.
         *
         * @property entityId Network entity ID
         */
        data class Entity(
            val entityId: Int
        ) : FocusData()

        /**
         * Focus on a specific bone of entity's model.
         *
         * Bone position is resolved client-side each frame, allowing
         * the camera to follow animated model parts smoothly.
         *
         * @property entityId Network entity ID
         * @property boneName Name of bone in model (e.g., "head", "body")
         * @property offset Offset from bone position (in bone local space)
         * @property smooth Whether to apply smoothing to reduce jitter
         */
        data class Bone(
            val entityId: Int,
            val boneName: String,
            val offset: Vec3,
            val smooth: Boolean
        ) : FocusData()
    }

    companion object {
        val ID = cobblemonResource("camera_focus")

        fun decode(buffer: RegistryFriendlyByteBuf): CameraFocusPacket {
            val cameraId = buffer.readUUID()

            val focusType = buffer.readByte().toInt()
            val focus: FocusData = when (focusType) {
                0 -> FocusData.None

                1 -> FocusData.Point(
                    position = Vec3(
                        buffer.readDouble(),
                        buffer.readDouble(),
                        buffer.readDouble()
                    )
                )

                2 -> FocusData.Entity(
                    entityId = buffer.readVarInt()
                )

                3 -> FocusData.Bone(
                    entityId = buffer.readVarInt(),
                    boneName = buffer.readUtf(64),
                    offset = Vec3(
                        buffer.readDouble(),
                        buffer.readDouble(),
                        buffer.readDouble()
                    ),
                    smooth = buffer.readBoolean()
                )

                else -> {
                    throw IllegalArgumentException("Unknown focus type: $focusType")
                }
            }

            return CameraFocusPacket(cameraId, focus)
        }
    }
}
