/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.portrait

import com.cobblemon.mod.common.pokemon.Pokemon
import com.mojang.blaze3d.vertex.PoseStack

/**
 * Responsible for drawing a Pokemon portrait.
 *
 * Keep in mind any scaling and handling of the [PoseStack] must be done on the implementations using this system.
 */
fun interface PortraitDrawer {

    /**
     * Draws the portrait.
     *
     * @param pokemon The [Pokemon] being drawn.
     * @param poseStack The [PoseStack] being used.
     * @param partialTicks The partial ticks of the screen using this.
     * @param isSelected If the [pokemon] is considered in a selected state for example hovered or the current party member.
     * @param index The index of the [pokemon] in the screen using this.
     */
    fun draw(
        pokemon: Pokemon,
        poseStack: PoseStack,
        partialTicks: Float,
        isSelected: Boolean,
        index: Int
    )

}