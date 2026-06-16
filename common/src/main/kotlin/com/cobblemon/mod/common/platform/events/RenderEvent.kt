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
         * After [LevelRenderer.renderLevel] has finished — on a shaderpack this is after Iris/OptiFine
         * has composited the world and restored vanilla shaders, so draws here are NOT intercepted by
         * the pack and render with vanilla GL while still depth-testing against the full world depth.
         * Used to draw the colored move-glow ([com.cobblemon.mod.common.client.render.MoveGlowRenderer])
         * so it tints the scene in the move's color identically under every pack.
         */
        POST_LEVEL
    }
}