/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.generic

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis

internal object BedrockEntityTransform {
    fun apply(stack: PoseStack, yaw: Float, scale: Float, pitch: Float = 0f) {
        stack.mulPose(Axis.YP.rotationDegrees(180f - yaw))
        stack.mulPose(Axis.XP.rotationDegrees(-pitch))
        // Match living entities: reflecting only Y reverses face winding and culls the outside.
        stack.scale(-scale, -scale, scale)
    }
}
