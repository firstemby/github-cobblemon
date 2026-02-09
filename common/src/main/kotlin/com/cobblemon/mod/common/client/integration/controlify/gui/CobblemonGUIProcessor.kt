/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.integration.controlify.gui

import com.cobblemon.mod.common.client.integration.controlify.CobblemonControlifyEntrypoint
import com.cobblemon.mod.common.client.integration.controlify.gui.component.ControllerComponentManager
import com.cobblemon.mod.common.client.integration.controlify.gui.component.ControllerComponentManager.ButtonGuideType
import com.cobblemon.mod.common.client.integration.controlify.gui.component.ControllerScreen
import dev.isxander.controlify.Controlify
import dev.isxander.controlify.InputMode
import dev.isxander.controlify.bindings.ControlifyBindings
import dev.isxander.controlify.bindings.InputBindingSupplierImpl
import dev.isxander.controlify.controller.ControllerEntity
import dev.isxander.controlify.controller.input.GamepadInputs
import dev.isxander.controlify.font.BindingFontHelper
import dev.isxander.controlify.gui.guide.GuideRenderer
import dev.isxander.controlify.gui.guide.PrecomputedLines
import dev.isxander.controlify.screenop.ScreenProcessor
import dev.isxander.controlify.virtualmouse.VirtualMouseBehaviour
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

open class CobblemonGUIProcessor(open val screen: Screen) : ScreenProcessor<Screen>(screen) {
    companion object {
        val typeToGlyth = mutableMapOf<ButtonGuideType, () -> Component>(
            ButtonGuideType.PRESS to { ControlifyBindings.GUI_PRESS.inputGlyph() },
            ButtonGuideType.BACK to { ControlifyBindings.GUI_BACK.inputGlyph() },
            ButtonGuideType.SPECIAL_1 to { ControlifyBindings.GUI_ABSTRACT_ACTION_1.inputGlyph() },
            ButtonGuideType.SPECIAL_2 to { ControlifyBindings.GUI_ABSTRACT_ACTION_2.inputGlyph() },
            ButtonGuideType.TAB to { Component.empty().append(ControlifyBindings.GUI_PREV_TAB.inputGlyph()).append(ControlifyBindings.GUI_NEXT_TAB.inputGlyph()) },
            ButtonGuideType.SECONDARY_TAB to { Component.empty().append(CobblemonControlifyEntrypoint.GUI_SECONDARY_PREV_TAB!!.inputGlyph()).append(CobblemonControlifyEntrypoint.GUI_SECONDARY_NEXT_TAB!!.inputGlyph()) },
            ButtonGuideType.START to { CobblemonControlifyEntrypoint.GUI_START!!.inputGlyph() },
            ButtonGuideType.JOYSTICK_HORIZONTAL to { Component.empty().append(ControlifyBindings.GUI_SECONDARY_NAVI_LEFT.inputGlyph()).append(ControlifyBindings.GUI_SECONDARY_NAVI_RIGHT.inputGlyph()) },
            ButtonGuideType.JOYSTICK_VERTICAL to { Component.empty().append(ControlifyBindings.GUI_SECONDARY_NAVI_UP.inputGlyph()).append(ControlifyBindings.GUI_SECONDARY_NAVI_DOWN.inputGlyph()) }
        )
    }

    var editBoxWidget: EditBox? = null

    // Until someone with more dedication than me adds non-vmouse support, we will make heavy use of the vmouse so make that the defualt for us
    override fun virtualMouseBehaviour(): VirtualMouseBehaviour {
        if (screen is ControllerScreen) {
            return VirtualMouseBehaviour.DISABLED
        }
        return VirtualMouseBehaviour.CURSOR_ONLY
    }

    override fun onWidgetRebuild() {
        (screen as? ControllerScreen)?.getComponentManager()?.let {
            it.active = Controlify.instance().let { it.currentInputMode().isController && !it.virtualMouseHandler().isVirtualMouseEnabled }
            it.openKeyboard = { editBox ->
                editBoxWidget = editBox
            }
            it.getVisual = { location ->
                InputBindingSupplierImpl(location).inputGlyph()
            }
        }
    }

    override fun onInputModeChanged(mode: InputMode) {
        (screen as? ControllerScreen)?.getComponentManager()?.let {
            val active = mode.isController && !Controlify.instance().virtualMouseHandler().isVirtualMouseEnabled
            it.active = active
        }
    }

    override fun onVirtualMouseToggled(enabled: Boolean) {
        (screen as? ControllerScreen)?.getComponentManager()?.let{
            it.active = Controlify.instance().currentInputMode().isController && !enabled
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
                componentManager.press()
            }
            if (ControlifyBindings.GUI_BACK.on(controller).justPressed()) {
                componentManager.onBack.invoke()
            }
            //Handling Special
            if (ControlifyBindings.GUI_ABSTRACT_ACTION_1.on(controller).justPressed()) {
                componentManager.special1()
            }
            if (ControlifyBindings.GUI_ABSTRACT_ACTION_2.on(controller).justPressed()) {
                componentManager.special2()
            }
            if (CobblemonControlifyEntrypoint.GUI_START!!.on(controller).justPressed()) {
                componentManager.onStart.invoke()
            }
            //Handling RJoystick
            val guiNaviRight = ControlifyBindings.GUI_SECONDARY_NAVI_RIGHT.on(controller)
            val guiNaviLeft = ControlifyBindings.GUI_SECONDARY_NAVI_LEFT.on(controller)
            val guiNaviUp = ControlifyBindings.GUI_SECONDARY_NAVI_UP.on(controller)
            val guiNaviDown = ControlifyBindings.GUI_SECONDARY_NAVI_DOWN.on(controller)

            if (guiNaviRight.digitalNow()) {
                componentManager.onRHorizontalHeld.invoke(true)
                if (!guiNaviRight.digitalPrev() || repeatAvailable) {
                    holdRepeatHelper.reset()
                    componentManager.onRHorizontal.invoke(true)
                }
            } else if (guiNaviLeft.digitalNow()) {
                componentManager.onRHorizontalHeld.invoke(false)
                if (!guiNaviLeft.digitalPrev() || repeatAvailable) {
                    holdRepeatHelper.reset()
                    componentManager.onRHorizontal.invoke(false)
                }
            }
            if (guiNaviUp.digitalNow()) {
                componentManager.onRVerticalHeld.invoke(true)
                if (!guiNaviUp.digitalPrev() || repeatAvailable) {
                    holdRepeatHelper.reset()
                    componentManager.onRVertical.invoke(true)
                }
            } else if (guiNaviDown.digitalNow()) {
                componentManager.onRVerticalHeld.invoke(false)
                if (!guiNaviDown.digitalPrev() || repeatAvailable) {
                    holdRepeatHelper.reset()
                    componentManager.onRVertical.invoke(false)
                }
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

        if (editBoxWidget != null) {
            tryOpenKeyboard(controller, editBoxWidget)
            editBoxWidget = null
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

    override fun render(controller: ControllerEntity, graphics: GuiGraphics, tickDelta: Float) {
        if (!controller.genericConfig().config().showScreenGuides) return
        val manager = (screen as? ControllerScreen)?.getComponentManager() ?: return
        if (!manager.active) return

        val minecraft = Minecraft.getInstance()

        val guideList = manager.buttonGuides.filter { it.condition.invoke() }

        val font = minecraft.font

        val leftLinesBuilder = PrecomputedLines.Builder()
        val rightLinesBuilder = PrecomputedLines.Builder()

        guideList.forEach { guide ->
            val builder = if (guide.rightAligned) rightLinesBuilder else leftLinesBuilder

            val glyphAfter = font.isBidirectional.xor(guide.rightAligned)
            val text = Component.empty()
                .append(if (glyphAfter) guide.text else typeToGlyth[guide.type]!!.invoke())
                .append(" ")
                .append(if (!glyphAfter) guide.text else typeToGlyth[guide.type]!!.invoke())

            val ruleNameWidth: Int = font.width(guide.text)
            val width = font.width(text)
            val glyphWidth = width - ruleNameWidth
            val backgroundLeft = if (glyphAfter) 0 else glyphWidth
            val backgroundRight = backgroundLeft + ruleNameWidth

            val height = BindingFontHelper.getComponentHeight(font, typeToGlyth[guide.type]!!.invoke())

            builder.addLine(text, width, height, backgroundLeft, backgroundRight)
        }

        val method = GuideRenderer::class.java.declaredMethods.first { it.name == "renderLines" } //We love Java Reflections
        method.isAccessible = true
        method.invoke(null,graphics, leftLinesBuilder.build(), font, minecraft.window.guiScaledWidth, minecraft.window.guiScaledHeight, false, false, true)
        method.invoke(null,graphics, rightLinesBuilder.build(), font, minecraft.window.guiScaledWidth, minecraft.window.guiScaledHeight, false, true, true)
    }
}