/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.integration.controlify.gui.component

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.screens.Screen
import kotlin.reflect.typeOf

open class ControllerComponent {
    var left: () -> ControllerComponent? = { null }
    var right: () -> ControllerComponent? = { null }
    var up: () -> ControllerComponent? = { null }
    var down: () -> ControllerComponent? = { null }

    var onPress: (() -> Unit)? = null
    var onSpecial1: () -> Unit = {}
    var onSpecial2: () -> Unit = {}

    var onSelect: () -> Unit = {}
    var onUnselect: () -> Unit = {}

    var onFocus: () -> Unit = {}
    var onUnfocus: () -> Unit = {}

    var associatedGUIElement: GuiEventListener? = null

    fun pairLeft(leftComponent: ControllerComponent?) {
        left = { leftComponent }
        leftComponent?.right = { this }
    }

    fun pairRight(rightComponent: ControllerComponent?) {
        right = { rightComponent }
        rightComponent?.left = { this }
    }

    fun pairUp(upComponent: ControllerComponent?) {
        up = { upComponent }
        upComponent?.down = { this }
    }

    fun pairDown(downComponent: ControllerComponent?) {
        down = { downComponent }
        downComponent?.up = { this }
    }
}