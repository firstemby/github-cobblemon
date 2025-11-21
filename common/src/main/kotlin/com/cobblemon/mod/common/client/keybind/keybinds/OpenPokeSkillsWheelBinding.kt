package com.cobblemon.mod.common.client.keybind.keybinds

import com.cobblemon.mod.common.client.keybind.CobblemonKeyBinding
import com.cobblemon.mod.common.client.keybind.KeybindCategories
import com.cobblemon.mod.common.net.messages.server.pokemon.skills.PokeSkillMinePacket
import com.mojang.blaze3d.platform.InputConstants

object OpenPokeSkillsWheelBinding : CobblemonKeyBinding(
    "key.cobblemon.open_poke_skills_wheel",
    InputConstants.Type.KEYSYM,
    InputConstants.KEY_B,
    KeybindCategories.COBBLEMON_CATEGORY
) {
    override fun onPress() {
        PokeSkillMinePacket().sendToServer()
    }
}