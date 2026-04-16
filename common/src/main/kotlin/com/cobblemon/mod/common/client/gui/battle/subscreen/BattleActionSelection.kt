/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.battle.subscreen

import com.cobblemon.mod.common.api.gui.ParentWidget
import com.cobblemon.mod.common.client.battle.SingleActionRequest
import com.cobblemon.mod.common.client.gui.battle.BattleGUI
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.MutableComponent

abstract class BattleActionSelection(
    val battleGUI: BattleGUI,
    val request: SingleActionRequest,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    name: MutableComponent
) : ParentWidget(x, y, width, height, name) {
    val opacity: Float
        get() = battleGUI.opacity

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if(mouseButtonEvent.button() == InputConstants.MOUSE_BUTTON_LEFT){
            return mousePrimaryClicked(mouseButtonEvent.x, mouseButtonEvent.y)
        }
        return super.mouseClicked(mouseButtonEvent, doubleClick)
    }

    abstract fun mousePrimaryClicked(mouseX: Double, mouseY: Double): Boolean
}