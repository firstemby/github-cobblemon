/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.traceFirstEntityCollision
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level


class WearableHatItem(override val name: String): CobblemonItem(Properties()), WearableItem {
    override fun use(level: Level, player: Player, usedHand: InteractionHand): InteractionResultHolder<ItemStack> {
        val itemStack = player.getItemInHand(usedHand)
        val hitPokemon = player.traceFirstEntityCollision(
            entityClass = PokemonEntity::class.java,
            maxDistance = player.entityInteractionRange().toFloat()
        )
        // If the player is looking at a Pokémon within range, block the ability to swap the item onto their head
        return if (player.isCrouching && hitPokemon != null && hitPokemon.isOwnedBy(player)) {
            InteractionResultHolder.fail(itemStack)
        } else this.swapWithEquipmentSlot(this, level, player, usedHand)
    }
}