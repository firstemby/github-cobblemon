/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.pokemon.ai

import com.cobblemon.mod.common.entity.pokemon.ai.PokemonMoveControl.Companion.HEADING_BLEND_SECONDS
import com.cobblemon.mod.common.util.math.InertialEase
import net.minecraft.world.phys.Vec3

/**
 * The direction a pokemon is travelling, eased so it turns smoothly. The server moves with it and the client runs
 * the same easing from the synced target so the body and animations see the same turn.
 */
class HeadingEase {
    val yaw = InertialEase(HEADING_BLEND_SECONDS, isAngle = true)
    // Minecraft pitch, negative is up
    val pitch = InertialEase(HEADING_BLEND_SECONDS, isAngle = true)

    /** Eases towards the direction steering wants */
    fun steer(targetYaw: Float, targetPitch: Float) {
        yaw.update(targetYaw)
        pitch.update(targetPitch)
    }

    /** Not steering so follow whatever else is turning the pokemon and level out */
    fun settle(currentYaw: Float, snap: Boolean) {
        if (snap) yaw.snap(currentYaw) else yaw.update(currentYaw)
        pitch.update(0F)
    }

    fun forward(): Vec3 = Vec3.directionFromRotation(pitch.value, yaw.value)
}
