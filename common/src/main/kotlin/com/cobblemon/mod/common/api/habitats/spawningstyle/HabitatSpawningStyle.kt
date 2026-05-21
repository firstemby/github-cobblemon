/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.habitats.spawningstyle

import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation

/**
 * Holds habitat block specific details about the methodology behind how the habitat block will spawn Pokémon. This is
 * either an [ActivatedHabitatSpawning] or a [NaturalHabitatSpawning].
 *
 * @see ActivatedHabitatSpawning
 * @see NaturalHabitatSpawning
 * @author Hiroku
 * @since February 14th, 2026
 */
sealed interface HabitatSpawningStyle {
    val type: ResourceLocation

    fun writeToNBT(nbt: CompoundTag)
    fun readFromNBT(nbt: CompoundTag)
}