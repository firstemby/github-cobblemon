/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.integration.controlify.gui.component

import net.minecraft.client.gui.screens.Screen

class ControllerComponentManager(val screen: Screen) {
    var active: Boolean = false
        get() = field
        set(value) {
            if (field != value) {
                field = value
                onActiveChange.invoke(value)
            }
            if (mainScreenManager()) {
                if (!value) {
                    screen.focused = null
                    focused?.onUnfocus?.invoke()
                } else {
                    screen.focused = focused?.associatedGUIElement
                    focused?.onFocus?.invoke()
                }
            }
        }

    var focused: ControllerComponent? = null
        get() = field
        set(value) {
            field?.onUnselect?.invoke()
            if (active) { field?.onUnfocus?.invoke() }
            field = value
            currentSelectedComponent = componentList.entries.firstOrNull { it.value == field }?.key ?: ""
            if (active) {
                field?.associatedGUIElement.let { screen.focused = it }
                field?.onFocus?.invoke()
            }
            field?.onSelect?.invoke()
        }

    val componentList: MutableMap<String, ControllerComponent> = mutableMapOf()
    var currentSelectedComponent = ""

    var onTabNav: (Boolean) -> Unit = {}
    var onSecondaryTabNav: (Boolean) -> Unit = {}
    var onBack: () -> Unit = {
        screen.onClose()
    }
    var onStart: () -> Unit = {}

    var onActiveChange: (Boolean) -> Unit = {}

    var onNoneSelected: () -> Boolean = { false }

    var onRHorizontalHeld: (Boolean) -> Unit = {}
    var onRVerticalHeld: (Boolean) -> Unit = {}

    var onRHorizontal: (Boolean) -> Unit = {}
    var onRVertical: (Boolean) -> Unit = {}

    fun left(): Boolean {
        if (focused == null && onNoneSelected.invoke()) return false
        focused?.left?.invoke()?.let {
            focused = it
            return false
        }
        return true
    }
    fun right(): Boolean {
        if (focused == null && onNoneSelected.invoke()) return false
        focused?.right?.invoke()?.let {
            focused = it
            return false
        }
        return true
    }
    fun up(): Boolean {
        if (focused == null && onNoneSelected.invoke()) return false
        focused?.up?.invoke()?.let {
            focused = it
            return false
        }
        return true
    }
    fun down(): Boolean {
        if (focused == null && onNoneSelected.invoke()) return false
        focused?.down?.invoke()?.let {
            focused = it
            return false
        }
        return true
    }

    fun onPress() = focused?.onPress?.invoke()
    fun onSpecial1() = focused?.onSpecial1?.invoke()
    fun onSpecial2() = focused?.onSpecial2?.invoke()

    private fun mainScreenManager(): Boolean {
        return screen is ControllerScreen && screen.getComponentManager() == this
    }
}