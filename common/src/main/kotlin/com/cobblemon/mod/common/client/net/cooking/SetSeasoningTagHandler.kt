package com.cobblemon.mod.common.client.net.cooking

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.block.campfirepot.CookingPotMenu
import com.cobblemon.mod.common.net.messages.client.cooking.SetSeasoningTagPacket
import net.minecraft.client.Minecraft

object SetSeasoningTagHandler : ClientNetworkPacketHandler<SetSeasoningTagPacket> {
    override fun handle(packet: SetSeasoningTagPacket, client: Minecraft) {
        val menu = client.player?.containerMenu as? CookingPotMenu ?: return
        menu.currentSeasoningTag = packet.seasoningTag
    }
}