package com.cobblemon.mod.common.client.gui.contentnav

import net.minecraft.client.gui.components.Button

class ButtonNav(val button: Button) : ContentNavElement<ButtonNav>() {
    override fun onUse() {
        button.onPress()
    }
}