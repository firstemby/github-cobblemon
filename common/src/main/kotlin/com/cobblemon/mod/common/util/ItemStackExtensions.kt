/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util

import com.google.gson.JsonElement
import com.mojang.serialization.JsonOps
import kotlin.jvm.optionals.getOrNull
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.world.InteractionHand
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

fun ItemStack.saveToJson(): JsonElement = JsonOps.INSTANCE.withEncoder(
    ItemStack.CODEC).apply(this).getOrThrow {
    return@getOrThrow IllegalStateException("Cant serialize ItemStack")
}

fun ItemStack.saveOptional(registryAccess: RegistryAccess): CompoundTag {
    val ops = registryAccess.createSerializationContext(NbtOps.INSTANCE)
    return ItemStack.OPTIONAL_CODEC.encodeStart(ops, this).result().getOrNull() as? CompoundTag ?: CompoundTag()
}

fun ItemStack.isHeld(player: ServerPlayer) = !isEmpty && (this == player.getItemInHand(InteractionHand.MAIN_HAND) || this == player.getItemInHand(InteractionHand.OFF_HAND))
fun ItemStack.isOf(tag: TagKey<Item>) = `is`(tag)
