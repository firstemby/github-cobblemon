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
import dev.isxander.controlify.Controlify
import dev.isxander.controlify.bindings.ControlifyBindings
import dev.isxander.controlify.controller.ControllerEntity
import dev.isxander.controlify.controller.input.GamepadInputs
import dev.isxander.controlify.screenop.ScreenProcessor
import dev.isxander.controlify.virtualmouse.VirtualMouseBehaviour
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.Direction

open class CobblemonGUIProcessor(open val screen: Screen) : ScreenProcessor<Screen>(screen) {
    val controllerComponentManager = ControllerComponentManager(screen)

    open val useBase: Boolean = true
    // Until someone with more dedication than me adds non-vmouse support, we will make heavy use of the vmouse so make that the defualt for us
    override fun virtualMouseBehaviour(): VirtualMouseBehaviour {
        return VirtualMouseBehaviour.DISABLED
    }

    override fun onControllerUpdate(controller: ControllerEntity) {
        if (useBase) { //Use default, but allow extended to use our setup
            super.onControllerUpdate(controller)
            return
        }

        Controlify.instance().virtualMouseHandler().handleControllerInput(controller)

        val repeatAvailable = holdRepeatHelper.canNavigate()

        val input = controller.input().orElseThrow()

        if (!Controlify.instance().virtualMouseHandler().isVirtualMouseEnabled) {
            //Handling Base
            if (ControlifyBindings.GUI_PRESS.on(controller).justPressed()) {
                handlePress(controller)
            }
            if (ControlifyBindings.GUI_BACK.on(controller).justPressed()) {
                handleBack(controller)
            }
            //Handling Special
            if (ControlifyBindings.GUI_ABSTRACT_ACTION_1.on(controller).justPressed()) {
                handleSpecial1(controller)
            }
            if (ControlifyBindings.GUI_ABSTRACT_ACTION_2.on(controller).justPressed()) {
                handleSpecial2(controller)
            }
            //Handle Navigation
            handleNavigation(controller)
        } else {
            handleScreenVMouse(controller, Controlify.instance().virtualMouseHandler())
        }

        //Main Tab
        if (ControlifyBindings.GUI_NEXT_TAB.on(controller).digitalNow() && (repeatAvailable || !ControlifyBindings.GUI_NEXT_TAB.on(controller).digitalPrev())) {
            holdRepeatHelper.reset()
            handleBaseTabNav(controller, NEXT_TAB)
        } else if (ControlifyBindings.GUI_PREV_TAB.on(controller).digitalNow() && (repeatAvailable || !ControlifyBindings.GUI_PREV_TAB.on(controller).digitalPrev())) {
            holdRepeatHelper.reset()
            handleBaseTabNav(controller, PREV_TAB)
        }

        //Secondary Tab
        if (CobblemonControlifyEntrypoint.GUI_SECONDARY_NEXT_TAB!!.on(controller).digitalNow() && (repeatAvailable || !CobblemonControlifyEntrypoint.GUI_SECONDARY_NEXT_TAB!!.on(controller).digitalPrev())) {
            holdRepeatHelper.reset()
            handleSecondaryTabNav(controller, NEXT_TAB)
        } else if (CobblemonControlifyEntrypoint.GUI_SECONDARY_PREV_TAB!!.on(controller).digitalNow() && (repeatAvailable || !CobblemonControlifyEntrypoint.GUI_SECONDARY_PREV_TAB!!.on(controller).digitalPrev())) {
            holdRepeatHelper.reset()
            handleSecondaryTabNav(controller, PREV_TAB)
        }
    }

    private fun handleNavigation(controller: ControllerEntity) {
        val repeatAvailable = holdRepeatHelper.canNavigate()

        val input = controller.input().orElseThrow()
        val state = input.stateNow()
        val prevState = input.stateThen()

        val guiNaviRight = ControlifyBindings.GUI_NAVI_RIGHT.on(controller)
        val guiNaviLeft = ControlifyBindings.GUI_NAVI_LEFT.on(controller)
        val guiNaviUp = ControlifyBindings.GUI_NAVI_UP.on(controller)
        val guiNaviDown = ControlifyBindings.GUI_NAVI_DOWN.on(controller)

        var direction: Direction? = null

        if (guiNaviRight.digitalNow() && (repeatAvailable || !guiNaviRight.digitalPrev())) {
            direction = Direction.EAST
            holdRepeatHelper.reset()
        } else if (guiNaviLeft.digitalNow() && (repeatAvailable || !guiNaviLeft.digitalPrev())) {
            direction = Direction.WEST
            holdRepeatHelper.reset()
        } else if (guiNaviUp.digitalNow() && (repeatAvailable || !guiNaviUp.digitalPrev())) {
            direction = Direction.NORTH
            holdRepeatHelper.reset()
        } else if (guiNaviDown.digitalNow() && (repeatAvailable || !guiNaviDown.digitalPrev())) {
            direction = Direction.SOUTH
            holdRepeatHelper.reset()
        } else if (state.isButtonDown(GamepadInputs.DPAD_RIGHT_BUTTON) && (repeatAvailable || !prevState.isButtonDown(GamepadInputs.DPAD_RIGHT_BUTTON))) {
            direction = Direction.EAST
            holdRepeatHelper.reset()
        } else if (state.isButtonDown(GamepadInputs.DPAD_LEFT_BUTTON) && (repeatAvailable || !prevState.isButtonDown(GamepadInputs.DPAD_LEFT_BUTTON))) {
            direction = Direction.WEST
            holdRepeatHelper.reset()
        } else if (state.isButtonDown(GamepadInputs.DPAD_UP_BUTTON) && (repeatAvailable || !prevState.isButtonDown(GamepadInputs.DPAD_UP_BUTTON))) {
            direction = Direction.NORTH
            holdRepeatHelper.reset()
        } else if (state.isButtonDown(GamepadInputs.DPAD_DOWN_BUTTON) && (repeatAvailable || !prevState.isButtonDown(GamepadInputs.DPAD_DOWN_BUTTON))) {
            direction = Direction.SOUTH
            holdRepeatHelper.reset()
        }

        if (direction != null) {
            handleGUINav(controller, direction)
        }
    }

    //These will be called respectively
    open fun handleBaseTabNav(controller: ControllerEntity, input: Int) {}
    open fun handleSecondaryTabNav(controller: ControllerEntity, input: Int) {}

    open fun handleSpecial1(controller: ControllerEntity) {}
    open fun handleSpecial2(controller: ControllerEntity) {}

    open fun handlePress(controller: ControllerEntity) {}
    open fun handleBack(controller: ControllerEntity) {}

    open fun handleGUINav(controller: ControllerEntity, direction: Direction) {}

    companion object {
        const val PREV_TAB = -1
        const val NEXT_TAB = 1
    }
}