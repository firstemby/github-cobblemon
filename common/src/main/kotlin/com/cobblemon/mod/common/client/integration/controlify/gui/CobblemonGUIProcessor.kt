/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.integration.controlify.gui

import com.cobblemon.mod.common.client.integration.controlify.gui.component.ControllerComponentManager
import com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves.MoveSwapScreen
import com.cobblemon.mod.common.client.integration.controlify.CobblemonControlifyEntrypoint
import com.cobblemon.mod.common.client.integration.controlify.gui.component.ControllerScreen
import dev.isxander.controlify.Controlify
import dev.isxander.controlify.InputMode
import dev.isxander.controlify.bindings.ControlifyBindings
import dev.isxander.controlify.controller.ControllerEntity
import dev.isxander.controlify.controller.input.GamepadInputs
import dev.isxander.controlify.screenop.ScreenProcessor
import dev.isxander.controlify.virtualmouse.VirtualMouseBehaviour
import dev.isxander.controlify.virtualmouse.VirtualMouseHandler
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.Direction

open class CobblemonGUIProcessor(open val screen: Screen) : ScreenProcessor<Screen>(screen) {
    // Until someone with more dedication than me adds non-vmouse support, we will make heavy use of the vmouse so make that the defualt for us
    override fun virtualMouseBehaviour(): VirtualMouseBehaviour {
        if (screen is ControllerScreen) {
            return VirtualMouseBehaviour.DEFAULT
        }
        return VirtualMouseBehaviour.CURSOR_ONLY
    }

    override fun onWidgetRebuild() {
        (screen as? ControllerScreen)?.getComponentManager()?.let {
            it.active = Controlify.instance().let { it.currentInputMode() == InputMode.CONTROLLER && !it.virtualMouseHandler().isVirtualMouseEnabled }
        }
    }

    override fun onInputModeChanged(mode: InputMode) {
        (screen as? ControllerScreen)?.getComponentManager()?.let {
            val active = mode == InputMode.CONTROLLER && !Controlify.instance().virtualMouseHandler().isVirtualMouseEnabled
            it.active = active
        }
    }

    override fun onVirtualMouseToggled(enabled: Boolean) {
        (screen as? ControllerScreen)?.getComponentManager()?.let{
            it.active = Controlify.instance().currentInputMode() == InputMode.CONTROLLER && !enabled
        }
    }

    override fun onControllerUpdate(controller: ControllerEntity) {
        val componentManager = (screen as? ControllerScreen)?.getComponentManager()

        if (componentManager == null) {
            super.onControllerUpdate(controller)
            return
        }

        //It was easier to fully handle this myself
        Controlify.instance().virtualMouseHandler().handleControllerInput(controller)

        val repeatAvailable = holdRepeatHelper.canNavigate()

        if (!Controlify.instance().virtualMouseHandler().isVirtualMouseEnabled) {
            //Handling Base
            if (ControlifyBindings.GUI_PRESS.on(controller).justPressed()) {
                componentManager.onPress()
                if (componentManager.focused?.associatedGUIElement is EditBox) {
                    screen.focused = componentManager.focused?.associatedGUIElement
                    tryOpenKeyboard(controller, componentManager.focused?.associatedGUIElement)
                }
            }
            if (ControlifyBindings.GUI_BACK.on(controller).justPressed()) {
                componentManager.onBack.invoke()
            }
            //Handling Special
            if (ControlifyBindings.GUI_ABSTRACT_ACTION_1.on(controller).justPressed()) {
                componentManager.onSpecial1()
            }
            if (ControlifyBindings.GUI_ABSTRACT_ACTION_2.on(controller).justPressed()) {
                componentManager.onSpecial2()
            }
            //Handle Navigation
            handleNavigation(controller, componentManager)
        } else {
            handleScreenVMouse(controller, Controlify.instance().virtualMouseHandler())
        }

        //Main Tab
        if (ControlifyBindings.GUI_NEXT_TAB.on(controller).digitalNow() && (repeatAvailable || !ControlifyBindings.GUI_NEXT_TAB.on(controller).digitalPrev())) {
            holdRepeatHelper.reset()
            componentManager.onTabNav.invoke(true)
        } else if (ControlifyBindings.GUI_PREV_TAB.on(controller).digitalNow() && (repeatAvailable || !ControlifyBindings.GUI_PREV_TAB.on(controller).digitalPrev())) {
            holdRepeatHelper.reset()
            componentManager.onTabNav.invoke(false)
        }

        //Secondary Tab
        if (CobblemonControlifyEntrypoint.GUI_SECONDARY_NEXT_TAB!!.on(controller).digitalNow() && (repeatAvailable || !CobblemonControlifyEntrypoint.GUI_SECONDARY_NEXT_TAB!!.on(controller).digitalPrev())) {
            holdRepeatHelper.reset()
            componentManager.onSecondaryTabNav.invoke(true)
        } else if (CobblemonControlifyEntrypoint.GUI_SECONDARY_PREV_TAB!!.on(controller).digitalNow() && (repeatAvailable || !CobblemonControlifyEntrypoint.GUI_SECONDARY_PREV_TAB!!.on(controller).digitalPrev())) {
            holdRepeatHelper.reset()
            componentManager.onSecondaryTabNav.invoke(false)
        }
    }

    private fun handleNavigation(controller: ControllerEntity, componentManager: ControllerComponentManager) {
        val repeatAvailable = holdRepeatHelper.canNavigate()

        val input = controller.input().orElseThrow()
        val state = input.stateNow()
        val prevState = input.stateThen()

        val guiNaviRight = ControlifyBindings.GUI_NAVI_RIGHT.on(controller)
        val guiNaviLeft = ControlifyBindings.GUI_NAVI_LEFT.on(controller)
        val guiNaviUp = ControlifyBindings.GUI_NAVI_UP.on(controller)
        val guiNaviDown = ControlifyBindings.GUI_NAVI_DOWN.on(controller)

        if (guiNaviRight.digitalNow() && (repeatAvailable || !guiNaviRight.digitalPrev())) {
            componentManager.right()
            holdRepeatHelper.reset()
        } else if (guiNaviLeft.digitalNow() && (repeatAvailable || !guiNaviLeft.digitalPrev())) {
            componentManager.left()
            holdRepeatHelper.reset()
        } else if (guiNaviUp.digitalNow() && (repeatAvailable || !guiNaviUp.digitalPrev())) {
            componentManager.up()
            holdRepeatHelper.reset()
        } else if (guiNaviDown.digitalNow() && (repeatAvailable || !guiNaviDown.digitalPrev())) {
            componentManager.down()
            holdRepeatHelper.reset()
        } else if (state.isButtonDown(GamepadInputs.DPAD_RIGHT_BUTTON) && (repeatAvailable || !prevState.isButtonDown(GamepadInputs.DPAD_RIGHT_BUTTON))) {
            componentManager.right()
            holdRepeatHelper.reset()
        } else if (state.isButtonDown(GamepadInputs.DPAD_LEFT_BUTTON) && (repeatAvailable || !prevState.isButtonDown(GamepadInputs.DPAD_LEFT_BUTTON))) {
            componentManager.left()
            holdRepeatHelper.reset()
        } else if (state.isButtonDown(GamepadInputs.DPAD_UP_BUTTON) && (repeatAvailable || !prevState.isButtonDown(GamepadInputs.DPAD_UP_BUTTON))) {
            componentManager.up()
            holdRepeatHelper.reset()
        } else if (state.isButtonDown(GamepadInputs.DPAD_DOWN_BUTTON) && (repeatAvailable || !prevState.isButtonDown(GamepadInputs.DPAD_DOWN_BUTTON))) {
            componentManager.down()
            holdRepeatHelper.reset()
        }
    }
}