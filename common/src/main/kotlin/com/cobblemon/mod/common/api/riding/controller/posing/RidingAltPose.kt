/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.riding.controller.posing

/**
 * The pose that will be used to determine if an alternate pose should be used while riding.
 *
 * @author Jackowes
 * @since March 23rd, 2025
 */
enum class RidingAltPose {
    NONE,
    GLIDING,
    SPRINTING,
    BRAKING,
    JUMPING;

    companion object {
        fun fromString(name: String): RidingAltPose? {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        }
    }
}