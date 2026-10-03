/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.pokemon

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.math.geometry.toRadians
import net.minecraft.util.Mth
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Tilts an unridden pokemon's whole model with how it's climbing or diving, the same way ridden flyers are turned
 * by their orientation. Walkers always travel level so they never tilt.
 */
object ModelPitch {
    // Most the model tilts either way
    const val MAX_TILT_DEGREES = 45F

    /** Minecraft pitch to tilt by. Only as much as travel is horizontal */
    fun tiltDegrees(entity: PokemonEntity, partialTick: Float): Float {
        if (entity.passengers.isNotEmpty()) return 0F
        val pitch = entity.heading.pitch.valueAt(partialTick)
        return (pitch * Mth.cos(pitch.toRadians())).coerceIn(-MAX_TILT_DEGREES, MAX_TILT_DEGREES)
    }

    /** Built the same way as OrientationController.rotate so it lines up with ridden orientation */
    fun orientation(yaw: Float, tilt: Float): Quaternionf {
        return Quaternionf().rotateY(-(yaw - 180F).toRadians()).rotateX(-tilt.toRadians())
    }

    /** Pivots orientation around the middle of the hitbox */
    fun aroundCentre(entity: PokemonEntity, orientation: Quaternionf): Matrix4f {
        val centre = Vector3f(0F, entity.bbHeight / 2, 0F)
        return Matrix4f().translate(centre).rotate(orientation).translate(centre.negate(Vector3f()))
    }
}
