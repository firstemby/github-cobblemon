/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.cooking

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.block.campfirepot.CookingPotMenu
import com.cobblemon.mod.common.block.campfirepot.CookingPotResultSlot
import com.cobblemon.mod.common.net.messages.client.cooking.SetItemPreviewPacket
import net.minecraft.client.Minecraft

object SetItemPreviewHandler : ClientNetworkPacketHandler<SetItemPreviewPacket> {
    override fun handle(packet: SetItemPreviewPacket, client: Minecraft) {
        val menu = client.player?.containerMenu as? CookingPotMenu ?: return
        (menu.getResultSlot() as CookingPotResultSlot).setPreview(packet.itemStack)
    }
}