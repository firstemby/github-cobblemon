/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.sound

import com.cobblemon.mod.common.client.sound.instances.BattleMusicInstance
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import net.minecraft.client.Minecraft
import net.minecraft.client.sounds.SoundManager
import net.minecraft.sounds.SoundEvents
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@BootstrapMinecraft
class BattleMusicControllerTest {
    @Test
    fun `intro replacement stops old track and stale sequence cannot replace later music`() {
        val client = mockk<Minecraft>(relaxed = true)
        val manager = mockk<SoundManager>(relaxed = true)
        mockkStatic(Minecraft::class)
        every { Minecraft.getInstance() } returns client
        every { client.soundManager } returns manager
        every { manager.isActive(any()) } returns false
        try {
            val previous = BattleMusicInstance(SoundEvents.EMPTY, 1f, 1f)
            BattleMusicController.initializeMusic(previous)
            BattleMusicController.initializeIntroWithLoop(SoundEvents.EMPTY, 1f, 1f, SoundEvents.EMPTY, 1f, 1f)
            val firstIntro = BattleMusicController.music
            assertFalse(firstIntro.isLooping)
            verify(exactly = 1) { manager.stop(previous) }
            BattleMusicController.initializeIntroWithLoop(SoundEvents.EMPTY, 1f, 1f, SoundEvents.EMPTY, 1f, 1f)
            verify(exactly = 1) { manager.stop(firstIntro) }
            BattleMusicController.tick()
            val loop = BattleMusicController.music
            assertTrue(loop.isLooping)
            BattleMusicController.tick()
            verify(exactly = 1) { manager.play(loop) }

            BattleMusicController.initializeIntroWithLoop(SoundEvents.EMPTY, 1f, 1f, SoundEvents.EMPTY, 1f, 1f)
            val replacement = BattleMusicInstance(SoundEvents.EMPTY, 1f, 1f)
            BattleMusicController.initializeMusic(replacement)
            BattleMusicController.tick()
            assertSame(replacement, BattleMusicController.music)
        } finally {
            BattleMusicController.endMusic()
            unmockkStatic(Minecraft::class)
        }
    }
}
