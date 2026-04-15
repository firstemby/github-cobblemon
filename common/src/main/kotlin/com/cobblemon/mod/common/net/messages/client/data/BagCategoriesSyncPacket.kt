package com.cobblemon.mod.common.net.messages.client.data

import com.cobblemon.mod.common.api.bag.BagCategories
import com.cobblemon.mod.common.api.bag.BagCategory
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

class BagCategoriesSyncPacket(categories: List<BagCategory>): DataRegistrySyncPacket<BagCategory, BagCategoriesSyncPacket>(categories) {
    companion object {
        val ID = cobblemonResource("bag_categories")
        fun decode(buffer: RegistryFriendlyByteBuf) = BagCategoriesSyncPacket(emptyList()).apply { decodeBuffer(buffer) }
    }

    override val id = ID
    override fun encodeEntry(buffer: RegistryFriendlyByteBuf, entry: BagCategory) {
        entry.encode(buffer)
    }

    override fun decodeEntry(buffer: RegistryFriendlyByteBuf): BagCategory = BagCategory.decode(buffer)

    override fun synchronizeDecoded(entries: Collection<BagCategory>) {
        BagCategories.reload(entries.associateBy { it.identifier })
    }
}