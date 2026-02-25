/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.fabric.client.compat

import com.cobblemon.mod.common.client.render.camera.IrisCompat
import net.irisshaders.iris.api.v0.IrisApi

object FabricIrisCompat : IrisCompat {

    override fun isShadersActive(): Boolean {
        return try {
            IrisApi.getInstance().isShaderPackInUse
        } catch (_: Exception) {
            false
        }
    }
}
