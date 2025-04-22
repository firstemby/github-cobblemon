/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.portrait

import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.client.GraphicsStatus

/**
 * Draws portraits if the Pokemon is not fainted.
 *
 * Meant for client with [GraphicsStatus.FABULOUS].
 */
class FullyAnimatedPortraitDrawer : AnimatedPortraitDrawer() {

    override fun shouldAnimate(pokemon: Pokemon, isSelected: Boolean): Boolean {
        return !pokemon.isFainted()
    }

}