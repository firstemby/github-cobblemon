/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.integration.controlify.gui.component

class SubManagerComponent: ControllerComponent() {
    var subManager: () -> ControllerComponentManager? = { null }

    init {
        left = { subManager.invoke()?.left(); null }
        right = { subManager.invoke()?.right(); null }
        up = { subManager.invoke()?.up(); null }
        down = { subManager.invoke()?.down(); null }

        onPress = { subManager.invoke()?.press() }
        onSpecial1 = { subManager.invoke()?.special1() }
        onSpecial2 = { subManager.invoke()?.special2() }

        onFocus = {
            subManager.invoke()?.active = true
            subManager.invoke()?.let { it.screen.focused = it.focused?.associatedGUIElement }
            subManager.invoke()?.focused?.onFocus?.invoke()
        }
        onUnfocus = {
            subManager.invoke()?.active = false
            subManager.invoke()?.focused?.onUnfocus?.invoke()
        }
    }
}