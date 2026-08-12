/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.ai

import com.cobblemon.mod.common.api.conditional.RegistryLikeCondition
import net.minecraft.world.level.block.Block

class SleepPlace {
    val faces = mutableListOf<String>()
    val blocks = mutableListOf<RegistryLikeCondition<Block>>()
    val onlyAir = false
    val light: IntRange? = null
    val canSeeSky: Boolean? = null
    val skyLight: IntRange? = null

    fun allowsFloor() = faces.any { it.equals("floor", true) || it.equals("top", true) }
    fun allowsSides() = faces.any { it.equals("sides", true) }
    fun allowsCeiling() = faces.any { it.equals("ceiling", true) || it.equals("bottom", true) }
}