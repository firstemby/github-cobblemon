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

    /** Pending loop music to play after intro finishes. */
    private var pendingLoopMusic: BattleMusicInstance? = null
    
    /** Whether we're currently playing an intro that should transition to loop. */
    private var waitingForIntroEnd = false

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

    /**
     * Start intro music that plays once, then automatically switches to loop music when finished.
     *
     * @param introSound The intro sound event to play once.
     * @param introVolume Volume for intro.
     * @param introPitch Pitch for intro.
     * @param loopSound The loop sound event to play after intro finishes.
     * @param loopVolume Volume for loop.
     * @param loopPitch Pitch for loop.
     */
    fun initializeIntroWithLoop(
        introSound: SoundEvent,
        introVolume: Float,
        introPitch: Float,
        loopSound: SoundEvent,
        loopVolume: Float,
        loopPitch: Float
    ) {
        // Prepare loop music for when intro completes
        pendingLoopMusic = BattleMusicInstance(loopSound, loopVolume, loopPitch, shouldLoop = true)
        waitingForIntroEnd = true
        
        // Create intro music (non-looping)
        val introMusic = BattleMusicInstance(
            sound = introSound,
            volume = introVolume,
            pitch = introPitch,
            shouldLoop = false
        )
        
        initializeMusic(introMusic)
    }
    
    /**
     * Called every client tick to check if intro has finished and loop should start.
     * Should be called from a client tick event handler.
     */
    fun tick() {
        if (waitingForIntroEnd && pendingLoopMusic != null) {
            // Check if intro is no longer playing (finished naturally)
            if (!manager.isActive(music) && !music.isFading()) {
                val loopMusic = pendingLoopMusic!!
                pendingLoopMusic = null
                waitingForIntroEnd = false
                
                // Start loop music
                music = loopMusic
                manager.play(music)
            }
        }
    }

    /** Switch to a new [BattleMusicInstance] if one is already playing. */
    fun switchMusic(newMusic: BattleMusicInstance) {
        pendingLoopMusic = null
        waitingForIntroEnd = false
        manager.stop(music)
        music = newMusic
        manager.play(music)
    }

    /** Fade out and end the current [BattleMusicInstance]. */
    fun endMusic() {
        pendingLoopMusic = null
        waitingForIntroEnd = false
        music.setFade()
    }

}
