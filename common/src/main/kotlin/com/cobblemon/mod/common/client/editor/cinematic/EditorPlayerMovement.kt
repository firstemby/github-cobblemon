/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic

import net.minecraft.world.entity.player.Player

class EditorPlayerMovement {
    private data class SavedState(val player: Player, val noPhysics: Boolean, val flying: Boolean)

    private var savedState: SavedState? = null

    fun update(player: Player?, enabled: Boolean) {
        if (!enabled || savedState?.player !== player) restore()
        if (!enabled || player == null) return
        if (savedState == null) savedState = SavedState(player, player.noPhysics, player.abilities.flying)
        player.noPhysics = true
        player.abilities.flying = true
    }

    fun restore() {
        val state = savedState ?: return
        savedState = null
        state.player.noPhysics = state.noPhysics
        state.player.abilities.flying = state.flying
    }
}
