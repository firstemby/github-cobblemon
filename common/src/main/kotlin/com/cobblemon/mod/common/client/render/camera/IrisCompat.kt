/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.camera

/** Iris shares deferred frame state across cameras, so active shaders require serial feed captures. */
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
