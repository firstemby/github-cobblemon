/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.integration.controlify.gui

import com.cobblemon.mod.common.client.integration.controlify.gui.component.ControllerComponentManager
import dev.isxander.controlify.screenop.ScreenProcessor
import dev.isxander.controlify.virtualmouse.VirtualMouseBehaviour
import net.minecraft.client.gui.screens.Screen

open class CobblemonGUIProcessor(open val screen: Screen) : ScreenProcessor<Screen>(screen) {
    val controllerComponentManager = ControllerComponentManager(screen)

    override fun virtualMouseBehaviour(): VirtualMouseBehaviour {
        return VirtualMouseBehaviour.DISABLED
    }
}