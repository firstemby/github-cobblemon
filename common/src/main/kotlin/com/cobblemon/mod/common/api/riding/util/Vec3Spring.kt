/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.riding.util

import net.minecraft.world.phys.Vec3

/**
 * A general medium for riding. Mainly used for categorizing which stats to pull from and for
 * presenting a simplified view of riding to users.
 *
 * @author Jackowes
 * @since March 27th, 2025
 */


class Vec3Spring(
    var value: Vec3 = Vec3.ZERO,
    var velocity: Vec3 = Vec3.ZERO
) {
    var previous: Vec3 = Vec3.ZERO

    fun update(target: Vec3, stiffness: Double, damping: Double) {
        previous = value

        //Hooke's Law f = kx where:
        //f is force
        //k is the spring constant or stiffness of this spring
        //x is the distance from rest and in this case the difference between the most recent
        //rotation delta ( the target ) and the springs current value for delta rot
        val force = target.subtract(value).scale(stiffness)

        //Now calculate acceleration and apply damping
        val accel = force.subtract(velocity.scale(damping))

        velocity = velocity.add(accel.scale(1.0/20.0))
        value = value.add(velocity.scale(1.0/20.0))
    }

    fun getInterpolated(partialTick: Double): Vec3 {
        val sprev = previous.scale(20.0)
        val svalue = value.scale(20.0)
        return sprev.add(svalue.subtract(sprev).scale(partialTick))
    }
}