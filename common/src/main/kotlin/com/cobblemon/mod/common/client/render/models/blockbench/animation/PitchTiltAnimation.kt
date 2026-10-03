/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.models.blockbench.animation

import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext

/**
 * Animation used to tilt the targeted bone of the entity by a pitch that is proportionate
 * to the direction the entity is moving. This is used to make birds and fish tilt upwards
 * or downwards based on their motion. The pitch comes from the eased heading locomotion travels on.
 *
 * @author Hiroku
 * @since August 9th, 2025
 */
class PitchTiltAnimation(
    val bone: Bone,
    // Not used anymore since the heading is already eased, kept so existing posers still load
    val maxChangePerTick: Float = 1F,
    val minPitch: Float = -45F,
    val maxPitch: Float = 45F,
) : PoseAnimation() {
    companion object {
        const val PREVIOUS_ANGLE = "previous_pitch_tilt_angle"
        const val CORRECTED_ANGLE = "corrected_pitch_tilt_angle"
        const val PITCHED_TILT = "has_pitched_tilt"
        // How much of the tilt has been applied this render, so poses blending together add up to one full tilt
        const val APPLIED_WEIGHT = "applied_pitch_tilt_weight"
    }

    override fun setupAnim(
        context: RenderContext,
        model: PosableModel,
        state: PosableState,
        limbSwing: Float,
        limbSwingAmount: Float,
        ageInTicks: Float,
        headYaw: Float,
        headPitch: Float,
        intensity: Float
    ) {
        // The whole model is tilted by ModelPitch now so this does nothing\
        //TODO: remove posers with this anim if we like ModelPitch
    }
}