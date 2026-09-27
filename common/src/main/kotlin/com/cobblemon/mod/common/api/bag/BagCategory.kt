/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.bag

import com.cobblemon.mod.common.util.readIdentifier
import com.cobblemon.mod.common.util.writeIdentifier
import net.minecraft.core.registries.Registries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.util.StringRepresentable
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

class BagCategory(
    identifier: ResourceLocation,
    val order: Int,
    val icon: ResourceLocation,
    val whiteListTag: TagKey<Item>,
    val blackListTag: TagKey<Item>,
    val maxCount: Int
): StringRepresentable {
    @Transient
    var identifier: ResourceLocation = identifier
        internal set

    override fun getSerializedName(): String = identifier.toString()

    fun getName(): MutableComponent = Component.translatable("${identifier.namespace}.bag_category.${identifier.path}")

    fun itemIsValid(itemStack: ItemStack): Boolean {
        if (itemStack.`is`(blackListTag)) return false
        if (itemStack.`is`(whiteListTag)) return true
        return false
    }

    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeIdentifier(identifier)
        buffer.writeInt(order)
        buffer.writeIdentifier(icon)
        buffer.writeIdentifier(whiteListTag.location)
        buffer.writeIdentifier(blackListTag.location)
        buffer.writeInt(maxCount)
    }

    companion object {
        internal fun decode(buffer: RegistryFriendlyByteBuf): BagCategory {
            return BagCategory(
                buffer.readIdentifier(),
                buffer.readInt(),
                buffer.readIdentifier(),
                TagKey.create(Registries.ITEM, buffer.readIdentifier()),
                TagKey.create(Registries.ITEM, buffer.readIdentifier()),
                buffer.readInt()
            )
        }
    }
}