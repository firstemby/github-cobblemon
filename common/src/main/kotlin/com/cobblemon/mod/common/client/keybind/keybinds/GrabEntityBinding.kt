package com.cobblemon.mod.common.client.keybind.keybinds

import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.client.keybind.CobblemonBlockingKeyBinding
import com.cobblemon.mod.common.client.keybind.KeybindCategories
import com.cobblemon.mod.common.net.messages.server.riding.GrabEntityPacket
import com.mojang.blaze3d.platform.InputConstants

object GrabEntityBinding : CobblemonBlockingKeyBinding(
    "key.cobblemon.grabentity",
    InputConstants.Type.KEYSYM,
    InputConstants.KEY_V,
    KeybindCategories.COBBLEMON_CATEGORY
) {
    override fun onPress() {
        CobblemonNetwork.sendToServer(GrabEntityPacket())
    }
}