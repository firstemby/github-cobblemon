/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.sound.instances

import net.minecraft.client.resources.sounds.EntityBoundSoundInstance
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity

/**
 * A class that acts as a "marker" for alpha cries. This is to avoid having to pass in entity info
 * to the [NeoforgeSoundEngineMixin] and [FabricSoundEngineMixin]. The play() mixin can just check for it
 * being an instance of [AlphaCrySoundInstance]
 */
class AlphaCrySoundInstance(
    sound: SoundEvent,
    source: SoundSource,
    volume: Float,
    pitch: Float,
    entity: Entity,
    seed: Long
) : EntityBoundSoundInstance(sound, source, volume, pitch, entity, seed)