package com.cobblemon.mod.common.net.messages.client.cooking

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.item.ItemStack

class SetItemPreviewPacket(val itemStack: ItemStack) : NetworkPacket<SetItemPreviewPacket> {
    companion object {
        val ID = cobblemonResource("set_item_preview")
        fun decode(buffer: RegistryFriendlyByteBuf): SetItemPreviewPacket {
            return SetItemPreviewPacket(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer))
        }
    }

    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, itemStack)
    }
}