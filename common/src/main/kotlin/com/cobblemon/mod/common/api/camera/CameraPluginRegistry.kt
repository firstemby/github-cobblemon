/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.camera

import com.cobblemon.mod.common.Cobblemon

/**
 * Registry for third-party mods to register custom easing functions.
 *
 * Built-in easing functions are handled directly by [CameraEasing][com.cobblemon.mod.common.client.camera.CameraEasing].
 * This registry allows addons to extend the available easing functions.
 */
object CameraPluginRegistry {

    private val easingFunctions = mutableMapOf<String, IEasingFunction>()

    fun registerEasingFunction(name: String, function: IEasingFunction) {
        val normalizedName = name.lowercase()
        if (easingFunctions.containsKey(normalizedName)) {
            Cobblemon.LOGGER.warn("[CameraPluginRegistry] Overwriting easing function '$name'")
        }
        easingFunctions[normalizedName] = function
    }

    fun getEasingFunction(name: String): IEasingFunction? {
        return easingFunctions[name.lowercase()]
    }
}
