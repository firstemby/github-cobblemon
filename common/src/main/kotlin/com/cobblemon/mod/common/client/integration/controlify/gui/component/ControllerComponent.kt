package com.cobblemon.mod.common.client.integration.controlify.gui.component

import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.screens.Screen

class ControllerComponent<T : Screen> {
    var left: (T) -> ControllerComponent<T>? = { null }
    var right: (T) -> ControllerComponent<T>? = { null }
    var up: (T) -> ControllerComponent<T>? = { null }
    var down: (T) -> ControllerComponent<T>? = { null }

    var associatedGUIElement: GuiEventListener? = null

    fun pairLeft(leftComponent: ControllerComponent<T>) {
        left = { leftComponent }
        leftComponent.right = { this }
    }

    fun pairRight(rightComponent: ControllerComponent<T>) {
        right = { rightComponent }
        rightComponent.left = { this }
    }

    fun pairUp(upComponent: ControllerComponent<T>) {
        up = { upComponent }
        upComponent.down = { this }
    }

    fun pairDown(downComponent: ControllerComponent<T>) {
        down = { downComponent }
        downComponent.up = { this }
    }
}