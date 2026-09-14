/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic

import com.cobblemon.mod.common.mixin.client.GameRendererMixin
import java.lang.reflect.ParameterizedType
import net.minecraft.client.renderer.GameRenderer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CinematicFovContractTest {
    @Test fun `cinematic FOV callback matches Minecraft double return type`() {
        val target = GameRenderer::class.java.declaredMethods.single { it.name == "getFov" }
        val injection = GameRendererMixin::class.java.declaredMethods.single { it.name.endsWith("overrideCinematicFov") }
        val callback = injection.genericParameterTypes.last() as ParameterizedType
        assertEquals(Double::class.javaPrimitiveType, target.returnType)
        assertEquals(Double::class.javaObjectType, callback.actualTypeArguments.single())
    }
}
