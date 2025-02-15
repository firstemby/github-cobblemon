/*
 * Copyright (C) 2025 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.models.blockbench.drone

import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel
import com.cobblemon.mod.common.client.render.models.blockbench.animation.PoseAnimation
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone
import com.cobblemon.mod.common.client.render.models.blockbench.quirk.ModelQuirk
import com.cobblemon.mod.common.entity.PoseType
import com.google.gson.annotations.SerializedName
import net.minecraft.client.model.geom.ModelPart

/**
 * A model for rendering the Rotom drone used by the stadium block.
 *
 * @author Flarkinater
 * @since February 14th, 2025
 */
class DroneModel(root: Bone) : PosableModel(root) {
    @Transient
    @SerializedName("So this name can be literally anything, huh?")
    override val rootPart = (root as ModelPart).registerChildWithAllChildren(root.children.entries.first().key)
    var maxScale = 1F
    var yTranslation = 0F
    var droneAnimations: Array<PoseAnimation> = emptyArray()
    var droneQuirks: Array<ModelQuirk<*>> = emptyArray()

    override fun registerPoses() {
        registerPose(
            poseType = PoseType.SLEEP,
            animations = droneAnimations,
            quirks = droneQuirks
        )
    }
}