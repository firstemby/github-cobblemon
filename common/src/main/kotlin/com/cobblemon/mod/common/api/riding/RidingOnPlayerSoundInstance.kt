/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.riding

import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource

/**
 * put what it is here
 *
 * @author Jackowes
 * @since March 4th, 2025
 */
class RidingOnPlayerSoundEventInstance(
    private val player: LocalPlayer,
    soundEvent: SoundEvent
) : AbstractTickableSoundInstance(soundEvent, SoundSource.PLAYERS, SoundInstance.createUnseededRandom()) {


    init {
        this.looping = true
        this.delay = 0
        this.volume = 0.1f
    }

    fun setPitch( pitch: Float ){
        this.pitch = pitch
    }

    fun setVolume( volume: Float){
        this.volume = volume
    }

    override fun tick() {
        //TODO: implement effectively
        //For now it should just play the sound?
        setPitch(1.0f)
        println("ticking")
    }

}