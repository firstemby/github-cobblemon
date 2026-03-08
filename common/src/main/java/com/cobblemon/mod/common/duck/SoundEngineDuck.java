/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.duck;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

public interface SoundEngineDuck {

    void pauseSounds(@Nullable Identifier id, @Nullable SoundSource category);

    void resumeSounds(@Nullable Identifier id, @Nullable SoundSource category);
}
