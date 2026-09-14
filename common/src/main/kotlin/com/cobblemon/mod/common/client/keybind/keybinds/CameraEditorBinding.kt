/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.keybind.keybinds

import com.cobblemon.mod.common.client.imgui.CobblemonImGui
import com.cobblemon.mod.common.client.keybind.CobblemonKeyBinding
import com.cobblemon.mod.common.client.keybind.KeybindCategories
import com.mojang.blaze3d.platform.InputConstants

object CameraEditorBinding : CobblemonKeyBinding(
    name = "key.cobblemon.camera_editor",
    type = InputConstants.Type.KEYSYM,
    key = InputConstants.KEY_F6,
    category = KeybindCategories.COBBLEMON_CATEGORY
) {
    override fun onPress() {
        CobblemonImGui.toggle()
    }
}
