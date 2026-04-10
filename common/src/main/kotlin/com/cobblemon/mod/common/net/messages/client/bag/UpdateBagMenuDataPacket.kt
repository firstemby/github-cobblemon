package com.cobblemon.mod.common.net.messages.client.bag

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

class UpdateBagMenuDataPacket(val categoryId: Int, val scroll: Int): NetworkPacket<UpdateBagMenuDataPacket> {
    companion object {
        val ID = cobblemonResource("update_bag_menu_data")
        fun decode(buffer: RegistryFriendlyByteBuf): UpdateBagMenuDataPacket {
            return UpdateBagMenuDataPacket(buffer.readInt(), buffer.readInt())
        }
    }

    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeInt(categoryId)
        buffer.writeInt(scroll)
    }
}