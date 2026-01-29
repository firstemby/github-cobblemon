/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.integration.controlify.gui.component

import com.cobblemon.mod.common.api.text.text
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents

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
            currentSelectedComponent = (componentList.entries.firstOrNull { it.value == value }?.key ?: "")
            field?.onUnselect?.invoke()
            if (active) { field?.onUnfocus?.invoke() }
            field = value
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

    var openKeyboard: (EditBox) -> Unit = {}
    val buttonGuides = mutableListOf<ButtonGuide>()
    var getVisual: (ResourceLocation) -> Component = { "".text() }

    var onPress: (() -> Unit)? = null
    var onSpecial1: () -> Unit = { focused?.onSpecial1?.invoke() }
    var onSpecial2: () -> Unit = { focused?.onSpecial2?.invoke() }

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

    fun press() {
        focused?.let {
            if (onPress != null) {
                onPress!!.invoke()
            } else {
                if (it.onPress != null) {
                    it.onPress!!.invoke()
                } else {
                    (it.associatedGUIElement as? Button)?.onPress()
                    (it.associatedGUIElement as? Button)?.playDownSound(Minecraft.getInstance().soundManager)
                    if (it.associatedGUIElement is EditBox) {
                        openKeyboard.invoke(it.associatedGUIElement as EditBox)
                        Minecraft.getInstance().soundManager.play(
                            SimpleSoundInstance.forUI(
                                SoundEvents.UI_BUTTON_CLICK,
                                1.0F
                            )
                        )
                    }
                }
            }
        }
    }
    fun special1() = onSpecial1.invoke()
    fun special2() = onSpecial2.invoke()

    private fun mainScreenManager(): Boolean {
        return screen is ControllerScreen && screen.getComponentManager() == this
    }

    data class ButtonGuide(
        val type: ButtonGuideType,
        val condition: () -> Boolean,
        val text: Component,
        val rightAligned: Boolean = false
    )

    enum class ButtonGuideType {
        PRESS,
        BACK,
        SPECIAL_1,
        SPECIAL_2,
        TAB,
        SECONDARY_TAB,
        START,
        JOYSTICK_HORIZONTAL,
        JOYSTICK_VERTICAL
    }
}