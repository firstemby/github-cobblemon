/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.integration.controlify.gui

import dev.isxander.controlify.screenop.ScreenProcessor
import dev.isxander.controlify.screenop.ScreenProcessorFactory
import dev.isxander.controlify.virtualmouse.VirtualMouseBehaviour
import net.minecraft.client.gui.screens.Screen

open class CobblemonGUIProcessor(open val screen: Screen) : ScreenProcessor<Screen>(screen) {
    // Until someone with more dedication than me adds non-vmouse support, we will make heavy use of the vmouse so make that the defualt for us
    override fun virtualMouseBehaviour(): VirtualMouseBehaviour {
        return VirtualMouseBehaviour.ENABLED
    }
}