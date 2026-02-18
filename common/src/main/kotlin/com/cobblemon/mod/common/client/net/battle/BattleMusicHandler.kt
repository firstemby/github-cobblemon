/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.battle

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.client.sound.BattleMusicController
import com.cobblemon.mod.common.client.sound.instances.BattleMusicInstance
import com.cobblemon.mod.common.net.messages.client.battle.BattleMusicPacket
import net.minecraft.client.Minecraft
import net.minecraft.sounds.SoundEvent

/**
 * The handler for [BattleMusicPacket]s. Interfaces with [BattleMusicController] to change battle music.
 *
 * @author Segfault Guy
 * @since April 22nd, 2023
 */
object BattleMusicHandler : ClientNetworkPacketHandler<BattleMusicPacket> {

    override fun handle(packet: BattleMusicPacket, client: Minecraft) {
        val soundManager = client.soundManager
        val currMusic = BattleMusicController.music

        // If no music specified, end current music
        if (packet.music == null) {
            BattleMusicController.endMusic()
            return
        }

        val musicEvent = SoundEvent.createVariableRangeEvent(packet.music!!)

        // Check if this is an intro + loop setup
        if (packet.loopMusic != null) {
            val loopEvent = SoundEvent.createVariableRangeEvent(packet.loopMusic!!)
            
            if (!soundManager.isActive(currMusic)) {
                // No music playing, start intro with loop
                BattleMusicController.initializeIntroWithLoop(
                    introSound = musicEvent,
                    introVolume = packet.volume,
                    introPitch = packet.pitch,
                    loopSound = loopEvent,
                    loopVolume = packet.loopVolume,
                    loopPitch = packet.loopPitch
                )
            } else if (currMusic.location == musicEvent.location && !packet.restartExisting) {
                // Same intro already playing, don't restart
                return
            } else {
                // Different music, switch to new intro with loop (controller handles transition)
                BattleMusicController.initializeIntroWithLoop(
                    introSound = musicEvent,
                    introVolume = packet.volume,
                    introPitch = packet.pitch,
                    loopSound = loopEvent,
                    loopVolume = packet.loopVolume,
                    loopPitch = packet.loopPitch
                )
            }
        } else {
            // Standard looping music (no intro)
            val newMusic = BattleMusicInstance(musicEvent, packet.volume, packet.pitch, shouldLoop = true)

            if (!soundManager.isActive(currMusic))
                BattleMusicController.initializeMusic(newMusic)
            else if (currMusic.location == newMusic.location && !packet.restartExisting)
                return
            else
                BattleMusicController.switchMusic(newMusic)
        }
    }
}
