/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.events.camera

import com.cobblemon.mod.common.api.camera.ICameraController
import com.cobblemon.mod.common.api.events.Cancelable
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import java.util.UUID

/** Base interface for all camera events. */
interface CameraEvent {
    val cameraId: UUID
    val controller: ICameraController
}

/** Events fired when a camera starts. Pre is cancelable. */
interface CameraStartedEvent : CameraEvent {
    val timelineId: String
    val viewers: List<ServerPlayer>

    /** Pre event - fired before camera starts. */
    data class Pre(
        override val cameraId: UUID,
        override val controller: ICameraController,
        override val timelineId: String,
        override val viewers: List<ServerPlayer>
    ) : CameraStartedEvent, Cancelable()

    /** Post event - fired after camera starts. */
    data class Post(
        override val cameraId: UUID,
        override val controller: ICameraController,
        override val timelineId: String,
        override val viewers: List<ServerPlayer>
    ) : CameraStartedEvent
}

/** Events fired when a camera stops. */
interface CameraStoppedEvent : CameraEvent {
    val reason: StopReason

    enum class StopReason {
        MANUAL,
        COMPLETED,
        VIEWER_LEFT,
        ERROR
    }

    /** Pre event - fired before camera stops. */
    data class Pre(
        override val cameraId: UUID,
        override val controller: ICameraController,
        override val reason: StopReason
    ) : CameraStoppedEvent

    /** Post event - fired after camera stops. */
    data class Post(
        override val cameraId: UUID,
        override val controller: ICameraController,
        override val reason: StopReason
    ) : CameraStoppedEvent
}

/** Fired when a camera transitions between states. */
data class CameraStateTransitionEvent(
    override val cameraId: UUID,
    override val controller: ICameraController,
    val fromStateId: String,
    val toStateId: String,
    val transitionType: String
) : CameraEvent

/** Fired when a viewer is added to a camera. */
data class CameraViewerAddedEvent(
    override val cameraId: UUID,
    override val controller: ICameraController,
    val viewer: ServerPlayer
) : CameraEvent

/** Fired when a viewer is removed from a camera. */
data class CameraViewerRemovedEvent(
    override val cameraId: UUID,
    override val controller: ICameraController,
    val viewer: ServerPlayer
) : CameraEvent

/** Fired every tick while a camera is active. Fires 20 times per second. */
data class CameraTickEvent(
    override val cameraId: UUID,
    override val controller: ICameraController,
    val currentPosition: Vec3,
    val currentFocus: Vec3?
) : CameraEvent
