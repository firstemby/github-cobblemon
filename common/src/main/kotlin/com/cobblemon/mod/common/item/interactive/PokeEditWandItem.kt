/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.interactive

import com.cobblemon.mod.common.client.gui.pokeedit.PokeEditGUI
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.item.CobblemonItem
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

class PokeEditWandItem : CobblemonItem(Properties().stacksTo(1)) {

    override fun use(world: Level, player: Player, usedHand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(usedHand)
        if (!player.isCreative) return InteractionResultHolder.pass(stack)
        if (world.isClientSide) {
            PokeEditGUI.open()
        }
        return InteractionResultHolder.sidedSuccess(stack, world.isClientSide)
    }

    override fun interactLivingEntity(
        stack: ItemStack,
        player: Player,
        entity: LivingEntity,
        usedHand: InteractionHand
    ): InteractionResult {
        if (!player.isCreative) return InteractionResult.PASS
        if (entity is PokemonEntity) {
            if (entity.level().isClientSide) {
                PokeEditGUI.open()
            }
            return InteractionResult.sidedSuccess(entity.level().isClientSide)
        }
        return InteractionResult.PASS
    }
}
