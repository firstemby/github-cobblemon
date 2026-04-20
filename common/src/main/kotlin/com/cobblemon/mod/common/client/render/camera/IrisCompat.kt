/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.camera

/**
 * Abstraction for Iris shader mod compatibility.
 *
 * Platform modules (Fabric, NeoForge) provide implementations that
 * directly call Iris's API to detect active shaders.
 *
 * Iris's deferred rendering pipeline (GBuffers, shadow maps, composite
 * passes) does not support multiple renderLevel() calls with different
 * camera positions in the same frame — depth/shadow state from the first
 * render contaminates the second. When Iris is active, camera feeds are
 * rendered one per frame (round-robin) to avoid this.
 */
interface IrisCompat {

    fun isShadersActive(): Boolean

    companion object {
        @JvmStatic
        var instance: IrisCompat = NoOpIrisCompat
    }
}

object NoOpIrisCompat : IrisCompat {
    override fun isShadersActive(): Boolean = false
}
