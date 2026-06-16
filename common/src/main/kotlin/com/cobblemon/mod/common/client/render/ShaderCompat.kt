/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render

import java.lang.reflect.Method

/**
 * Detects whether a shaderpack is currently active, via Iris (Fabric/NeoForge) or OptiFine.
 *
 * Every reference here is reflective and fully guarded: Iris/OptiFine are optional and may be
 * absent at runtime, so we never hard-reference their classes. If neither is present (or their
 * APIs ever change shape) the lookups fail silently during class init and [shadersActive] simply
 * returns `false`. This is the loader-safe contract the rest of the particle code relies on — it
 * must work identically on Fabric and NeoForge and when no shader mod is installed at all.
 *
 * The reflective handles are resolved once; only the cheap boolean accessor is invoked per call.
 * Particle render types call this at most a handful of times per frame (once per [begin]), so the
 * cost is negligible.
 */
object ShaderCompat {

    // Iris: net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse()
    // getInstance() returns a stable singleton, so it is safe to cache.
    private val irisInstance: Any?
    private val irisIsShaderPackInUse: Method?

    // OptiFine: net.optifine.Config.isShaders() (static)
    private val optifineIsShaders: Method?

    init {
        var irisInst: Any? = null
        var irisMethod: Method? = null
        try {
            val irisApi = Class.forName("net.irisshaders.iris.api.v0.IrisApi")
            irisInst = irisApi.getMethod("getInstance").invoke(null)
            irisMethod = irisApi.getMethod("isShaderPackInUse")
        } catch (ignored: Throwable) {
            irisInst = null
            irisMethod = null
        }
        irisInstance = irisInst
        irisIsShaderPackInUse = irisMethod

        var optifineMethod: Method? = null
        try {
            optifineMethod = Class.forName("net.optifine.Config").getMethod("isShaders")
        } catch (ignored: Throwable) {
            optifineMethod = null
        }
        optifineIsShaders = optifineMethod
    }

    /**
     * `true` when Iris or OptiFine reports an active (compiled and enabled) shaderpack. Never throws;
     * any reflective failure is treated as "no shaders".
     */
    fun shadersActive(): Boolean {
        val instance = irisInstance
        val irisMethod = irisIsShaderPackInUse
        if (instance != null && irisMethod != null) {
            try {
                if (irisMethod.invoke(instance) == true) return true
            } catch (ignored: Throwable) {}
        }

        val optifineMethod = optifineIsShaders
        if (optifineMethod != null) {
            try {
                if (optifineMethod.invoke(null) == true) return true
            } catch (ignored: Throwable) {}
        }

        return false
    }
}
