/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.integration.controlify.gui

import com.cobblemon.mod.common.client.gui.interact.wheel.InteractWheelGUI
import dev.isxander.controlify.bindings.ControlifyBindings
import dev.isxander.controlify.controller.ControllerEntity
import dev.isxander.controlify.controller.haptic.HapticEffects
import dev.isxander.controlify.screenop.ScreenControllerEventListener
import dev.isxander.controlify.screenop.ScreenProcessor
import dev.isxander.controlify.screenop.ScreenProcessorFactory
import dev.isxander.controlify.virtualmouse.VirtualMouseBehaviour
import net.minecraft.util.Mth
import kotlin.math.abs


// Lots of this is taken from the RadialMenu in Controlify to make this screen feel consistent with it
class InteractWheelProcessor(val screen: InteractWheelGUI) : ScreenProcessor<InteractWheelGUI>(screen), ScreenControllerEventListener {
    private var selectedButton = -1
    private var idleTicks: Int = 0
    private var idleTicksTimeout: Int? = null

    override fun virtualMouseBehaviour(): VirtualMouseBehaviour {
        return VirtualMouseBehaviour.DISABLED
    }

    init {
        addEventListener(this)
    }

    override fun onControllerInput(controller: ControllerEntity) {
        if (idleTicksTimeout == null) {
            idleTicksTimeout = controller.input().orElseThrow().confObj().radialButtonFocusTimeoutTicks
        }
        val x = ControlifyBindings.RADIAL_AXIS_RIGHT.on(controller).analogueNow()
        - ControlifyBindings.RADIAL_AXIS_LEFT.on(controller).analogueNow()
        val y = ControlifyBindings.RADIAL_AXIS_DOWN.on(controller).analogueNow()
        - ControlifyBindings.RADIAL_AXIS_UP.on(controller).analogueNow()
        val threshold = controller.input().orElseThrow().config().config().buttonActivationThreshold

        if (abs(x) >= threshold || abs(y) >= threshold) {
            val angle = Mth.wrapDegrees(Mth.RAD_TO_DEG * Mth.atan2(y.toDouble(), x.toDouble()) - 90f) + 180f
            val buttonCount = screen.buttons.size
            val each = 360f / buttonCount

            val newSelected = Mth.floor((angle + each / 2f) / each) % buttonCount
            if (newSelected != selectedButton) {
                selectedButton = newSelected
                controller.hdHaptics().ifPresent { haptics -> haptics.playHaptic(HapticEffects.NAVIGATE) }
            }

            for (button in 0..<buttonCount) {
                val selected = button == selectedButton
                screen.buttons[button].isFocused = selected
            }

            idleTicks = 0
        } else {
            idleTicks = idleTicks+ 1
            if (idleTicks>= idleTicksTimeout!! && selectedButton != -1) {
                selectedButton = -1
                for (button in screen.buttons) {
                    button.isFocused = false
                }
                controller.hdHaptics().ifPresent { haptics -> haptics.playHaptic(HapticEffects.NAVIGATE) }
            }
        }
    }

    class InteractWheelFactory : ScreenProcessorFactory.Factory<InteractWheelGUI> {
        override fun apply(t: InteractWheelGUI): ScreenProcessor<in InteractWheelGUI> = InteractWheelProcessor(t)
    }
}