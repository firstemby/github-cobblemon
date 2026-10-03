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
import com.cobblemon.mod.common.client.render.models.blockbench.addRotation
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.math.geometry.toRadians
import net.minecraft.util.Mth

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
        val entity = context.entity as? PokemonEntity ?: return
        if (entity.passengers.isNotEmpty()) {
            return
        }
        // Negative is up like Minecraft pitch
        val pitch = entity.heading.pitch.valueAt(Mth.frac(ageInTicks))
        // Only tilt as much as the travel is horizontal so straight climbs and dives stay level
        val tilt = (pitch * Mth.cos(pitch.toRadians())).coerceIn(minPitch, maxPitch)

        if (PITCHED_TILT !in state.renderMarkers) {
            state.numbers.remove(CORRECTED_ANGLE)
            val appliedAngle = tilt * intensity
            bone.addRotation(0, appliedAngle.toRadians())
            state.numbers[PREVIOUS_ANGLE] = appliedAngle
        }
        // A second tilt on the same model doesn't add more, the first already covers it
        state.renderMarkers += PITCHED_TILT
    }
}