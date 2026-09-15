/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.keybind.keybinds

import com.cobblemon.mod.common.client.keybind.CobblemonKeyBinding
import com.cobblemon.mod.common.client.keybind.KeybindCategories
import com.cobblemon.mod.common.client.net.stadium.StadiumSpectateStateHandler
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft

object StadiumSpectateBinding : CobblemonKeyBinding("key.cobblemon.stadium_spectate",
    InputConstants.Type.KEYSYM, InputConstants.KEY_V, KeybindCategories.COBBLEMON_CATEGORY) {
    override fun onTick() {
        StadiumSpectateStateHandler.tick()
        super.onTick()
    }

    override fun onPress() {
        if (Minecraft.getInstance().screen == null) StadiumSpectateStateHandler.toggle()
    }
}
