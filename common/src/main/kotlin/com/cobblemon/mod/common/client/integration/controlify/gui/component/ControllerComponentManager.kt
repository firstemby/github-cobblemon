package com.cobblemon.mod.common.client.integration.controlify.gui.component

import net.minecraft.client.gui.screens.Screen

class ControllerComponentManager<T : Screen>(val screen: T) {
    var focused: ControllerComponent<T>?
        get() = focused
        set(value) {
            focused = value
            focused?.associatedGUIElement?.let { screen.focused = it }
        }

    fun left() = focused?.left?.invoke(screen)?.let { focused = it }
    fun right() = focused?.right?.invoke(screen)?.let { focused = it }
    fun up() = focused?.up?.invoke(screen)?.let { focused = it }
    fun down() = focused?.down?.invoke(screen)?.let { focused = it }
}