/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.interactive

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.giveOrDropItemStack
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemNameBlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block

class VitaminItem(override val stat: Stat, block: Block): ItemNameBlockItem(block, Properties()), EVIncreaseItem {
    override val evIncreaseAmount: Int = 10

    override fun use(world: Level, user: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        if (user is ServerPlayer) {
            return use(user, user.getItemInHand(hand))
        }
        return InteractionResultHolder.success(user.getItemInHand(hand))
    }

    override fun onSuccessfulApply(player: ServerPlayer, stack: ItemStack, pokemon: Pokemon) {
        pokemon.entity?.playSound(CobblemonSounds.MEDICINE_PILLS_USE)
        stack.consume(1, player)
        if (!player.hasInfiniteMaterials()) {
            player.giveOrDropItemStack(Items.GLASS_BOTTLE.defaultInstance)
        }
    }
}