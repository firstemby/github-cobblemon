/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.sound

import com.cobblemon.mod.common.client.sound.instances.BattleMusicInstance
import com.cobblemon.mod.common.util.pauseSounds
import com.cobblemon.mod.common.util.resumeSounds
import net.minecraft.client.Minecraft
import net.minecraft.sounds.SoundSource
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents

/**
 * The controller for playing, switching, and stopping [BattleMusicInstance]s.
 *
 * @author Segfault Guy
 * @since October 31st, 2023
 */
object BattleMusicController {

    /** The last [BattleMusicInstance] played. */
    var music = BattleMusicInstance(SoundEvents.EMPTY, 0.0F, 0.0F)
        private set

    private data class Sequence(val intro: BattleMusicInstance, val loop: BattleMusicInstance)
    private var pendingSequence: Sequence? = null

    /**
     * The [SoundSource]s that are filtered while the [SoundManager] is playing a [BattleMusicInstance].
     *
     * Applicable [SoundInstance]s are blocked while a [BattleMusicInstance] is active, or paused if played before.
     */
    val filteredCategories = listOf(SoundSource.AMBIENT, SoundSource.MUSIC, SoundSource.RECORDS)

    private val manager = Minecraft.getInstance().soundManager

    /** Start a new [BattleMusicInstance] and pause all [filteredCategories] sounds currently playing. */
    fun initializeMusic(newMusic: BattleMusicInstance) {
        music = newMusic
        manager.play(music)
        if (manager.isActive(music)) {
            filteredCategories.forEach { manager.pauseSounds(null, it) }
            manager.resumeSounds(music.location, SoundSource.MUSIC) // lazy :)
        }
    }

    fun initializeIntroWithLoop(
        introSound: SoundEvent,
        introVolume: Float,
        introPitch: Float,
        loopSound: SoundEvent,
        loopVolume: Float,
        loopPitch: Float
    ) {
        val intro = BattleMusicInstance(introSound, introVolume, introPitch, shouldLoop = false)
        val loop = BattleMusicInstance(loopSound, loopVolume, loopPitch, shouldLoop = true)
        pendingSequence = null
        manager.stop(music)
        initializeMusic(intro)
        pendingSequence = Sequence(intro, loop)
    }

    fun tick() {
        val sequence = pendingSequence ?: return
        if (music !== sequence.intro || music.isFading()) {
            pendingSequence = null
            return
        }
        if (!manager.isActive(music)) {
            pendingSequence = null
            music = sequence.loop
            manager.play(music)
        }
    }

    /** Switch to a new [BattleMusicInstance] if one is already playing. */
    fun switchMusic(newMusic: BattleMusicInstance) {
        pendingSequence = null
        manager.stop(music)
        music = newMusic
        manager.play(music)
    }

    /** Fade out and end the current [BattleMusicInstance]. */
    fun endMusic() {
        pendingSequence = null
        music.setFade()
    }

}
