/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.platform.events

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Camera
import net.minecraft.client.DeltaTracker
import net.minecraft.client.renderer.LevelRenderer
import org.joml.Matrix4f

/**
 * Event fired during the various [Stage]s of the [LevelRenderer].
 *
 * @author Segfault Guy
 * @since September 11th, 2024
 */
data class RenderEvent(
    val stage: Stage,
    val levelRenderer: LevelRenderer,
    val poseStack: PoseStack,
    val modelViewMatrix: Matrix4f,
    val projectionMatrix: Matrix4f,
    val tickCounter: DeltaTracker,
    val camera: Camera
) {

    /** Represents when a layer is rendered by [LevelRenderer.renderLevel]. Ordinal corresponds with its render order. */
    enum class Stage {
        TRANSLUCENT,

        /**
         * Fired after the world framebuffer writes are complete but before the level renderer tears
         * down its camera view transform. Direct draws at [POST_LEVEL] can reuse the matrix captured
         * here when they need to render after shaderpack composition.
         */
        LAST,

        /**
         * Fired after [LevelRenderer.renderLevel] has fully finished — on a shaderpack, this is after
         * Iris/OptiFine has composited the world to the main framebuffer and restored vanilla shaders.
         * Draws here are NOT intercepted by the shaderpack, so they render with vanilla shaders while
         * still depth testing against the full (opaque + translucent) world depth. Used to render
         * Cobblemon's snowstorm particles so they look like vanilla under shaders.
         */
        POST_LEVEL
    }
}
