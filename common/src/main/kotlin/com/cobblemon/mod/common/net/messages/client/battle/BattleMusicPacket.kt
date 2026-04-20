/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.battle

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readIdentifier
import com.cobblemon.mod.common.util.writeIdentifier
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * Instructs a client what SoundEvent to play during a battle.
 *
 * Handled by [com.cobblemon.mod.common.client.net.battle.BattleMusicHandler].
 *
 * @property music The location of the sound to play during a battle. If null, gracefully ends the currently playing sound.
 * @property volume Volume for the music (0.0 to 1.0+).
 * @property pitch Pitch for the music.
 * @property restartExisting If true and the location of [music] matches the currently playing sound, will restart from the beginning.
 * @property loopMusic Optional music to play after [music] finishes. If provided, [music] plays once (intro) then [loopMusic] loops.
 * @property loopVolume Volume for the loop music.
 * @property loopPitch Pitch for the loop music.
 *
 * @author Segfault Guy
 * @since April 20th, 2023
 */
class BattleMusicPacket(
    var music: ResourceLocation? = null,
    var volume: Float = 1.0f,
    var pitch: Float = 1.0f,
    var restartExisting: Boolean = true,
    var loopMusic: ResourceLocation? = null,
    var loopVolume: Float = 1.0f,
    var loopPitch: Float = 1.0f
) : NetworkPacket<BattleMusicPacket> {

    companion object {
        val ID = cobblemonResource("battle_music")
        fun decode(buffer: RegistryFriendlyByteBuf) = BattleMusicPacket(
            music = buffer.readNullable { it.readIdentifier() },
            volume = buffer.readFloat(),
            pitch = buffer.readFloat(),
            restartExisting = buffer.readBoolean(),
            loopMusic = buffer.readNullable { it.readIdentifier() },
            loopVolume = buffer.readFloat(),
            loopPitch = buffer.readFloat()
        )
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeNullable(music) { buf, value -> buf.writeIdentifier(value) }
        buffer.writeFloat(volume)
        buffer.writeFloat(pitch)
        buffer.writeBoolean(restartExisting)
        buffer.writeNullable(loopMusic) { buf, value -> buf.writeIdentifier(value) }
        buffer.writeFloat(loopVolume)
        buffer.writeFloat(loopPitch)
    }
}