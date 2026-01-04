/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.camera

import com.cobblemon.mod.common.Cobblemon

object CameraPluginRegistry {

    private val easingFunctions = mutableMapOf<String, IEasingFunction>()
    private val coordinatorFactories = mutableMapOf<String, () -> ICameraCoordinator>()

    fun registerEasingFunction(name: String, function: IEasingFunction) {
        val normalizedName = name.lowercase()
        if (easingFunctions.containsKey(normalizedName)) {
            Cobblemon.LOGGER.warn("[CameraAPI] Overwriting easing function '$name'")
        }
        easingFunctions[normalizedName] = function
        Cobblemon.LOGGER.info("[CameraAPI] Registered easing function '$name'")
    }

    fun getEasingFunction(name: String): IEasingFunction? {
        return easingFunctions[name.lowercase()]
    }

    fun registerCoordinator(type: String, factory: () -> ICameraCoordinator) {
        val normalizedType = type.lowercase()
        if (coordinatorFactories.containsKey(normalizedType)) {
            Cobblemon.LOGGER.warn("[CameraAPI] Overwriting coordinator factory '$type'")
        }
        coordinatorFactories[normalizedType] = factory
        Cobblemon.LOGGER.info("[CameraAPI] Registered coordinator factory '$type'")
    }

    fun createCoordinator(type: String): ICameraCoordinator? {
        return coordinatorFactories[type.lowercase()]?.invoke()
    }

    fun listEasingFunctions(): Set<String> = easingFunctions.keys.toSet()

    fun listCoordinators(): Set<String> = coordinatorFactories.keys.toSet()

    internal fun clear() {
        easingFunctions.clear()
        coordinatorFactories.clear()
    }

    internal fun registerBuiltins() {
        Cobblemon.LOGGER.info("[CameraAPI] Camera plugin registry initialized")
    }
}
