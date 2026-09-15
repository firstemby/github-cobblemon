/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.cinematic

import com.cobblemon.mod.common.client.camera.CameraClientChunkCacheExtension
import com.cobblemon.mod.common.client.render.camera.CameraViewAreaExtension
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.mixin.client.CameraLevelRendererMixin
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import net.minecraft.client.multiplayer.ClientLevel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import java.lang.reflect.InvocationTargetException

@BootstrapMinecraft
class CameraLevelLifecycleTest {
    private val currentLevel = mockk<ClientLevel>()
    private val renderer = object : CameraLevelRendererMixin() {}

    @BeforeEach
    fun setup() {
        mockkStatic(CameraClientChunkCacheExtension::class, CameraViewAreaExtension::class)
        every { CameraClientChunkCacheExtension.clear() } answers {}
        every { CameraViewAreaExtension.clear() } answers {}
        CameraLevelRendererMixin::class.java.getDeclaredField("level")
            .apply { isAccessible = true }.set(renderer, currentLevel)
    }

    @AfterEach
    fun cleanup() {
        unmockkStatic(CameraClientChunkCacheExtension::class, CameraViewAreaExtension::class)
    }

    @Test
    fun `dimension change releases retained chunks and invalidates section references`() {
        setLevel(mockk())
        verify(exactly = 1) { CameraClientChunkCacheExtension.clear() }
        verify(exactly = 1) { CameraViewAreaExtension.clear() }
    }

    @Test
    fun `setting the same level preserves retained chunk ownership`() {
        setLevel(currentLevel)
        verify(exactly = 0) { CameraClientChunkCacheExtension.clear() }
        verify(exactly = 1) { CameraViewAreaExtension.clear() }
    }

    @Test
    fun `section references are invalidated when chunk cleanup fails during detach`() {
        every { CameraClientChunkCacheExtension.clear() } throws IllegalStateException("chunk cleanup failed")
        assertThrows(IllegalStateException::class.java) { setLevel(null) }
        verify(exactly = 1) { CameraViewAreaExtension.clear() }
    }

    private fun setLevel(level: ClientLevel?) {
        val callback = CameraLevelRendererMixin::class.java.getDeclaredMethod("cobblemon\$onSetLevel",
            ClientLevel::class.java, CallbackInfo::class.java).apply { isAccessible = true }
        try {
            callback.invoke(renderer, level, CallbackInfo("setLevel", false))
        } catch (exception: InvocationTargetException) {
            throw exception.cause!!
        }
    }
}
