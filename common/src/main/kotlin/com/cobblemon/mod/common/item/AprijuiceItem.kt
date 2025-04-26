/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item

import com.cobblemon.mod.common.CobblemonItemComponents
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.apricorn.Apricorn
import com.cobblemon.mod.common.api.cooking.Flavour
import com.cobblemon.mod.common.api.item.PokemonSelectingItem
import com.cobblemon.mod.common.api.riding.stats.RidingStat
import com.cobblemon.mod.common.pokemon.Nature
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

class AprijuiceItem(val type: Apricorn): CobblemonItem(Properties().stacksTo(16)), PokemonSelectingItem {
    override val bagItem = null

    companion object {
        const val DISLIKED_FLAVOUR_MULTIPLIER = 0.75F
        const val LIKED_FLAVOUR_MULTIPLIER = 1.25F

        const val STRONG_APRICORN_MULTIPLIER = 1.25F
        const val WEAK_APRICORN_MULTIPLIER = 0.75F
    }
    
    override fun canUseOnPokemon(stack: ItemStack, pokemon: Pokemon): Boolean {
        return getBoosts(stack, pokemon).any { pokemon.canAddRideBoost(it.key, it.value) }
    }

    fun getBoosts(stack: ItemStack, pokemon: Pokemon): Map<RidingStat, Float> {
        val flavours = stack.get(CobblemonItemComponents.FLAVOUR)?.flavours ?: emptyMap()
        return RidingStat.entries.associate { ridingStat ->
            val flavour = ridingStat.flavour
            val flavourValue = flavours[flavour]?.takeUnless { it == 0 } ?: return@associate (ridingStat to 0F)
            val adjustedValue = calculateRidingBoostForFlavour(flavour, type, flavourValue, pokemon.nature)
            ridingStat to adjustedValue
        }.filter { it.value > 0 }
    }

    override fun applyToPokemon(
        player: ServerPlayer,
        stack: ItemStack,
        pokemon: Pokemon
    ): InteractionResultHolder<ItemStack>? {
        val boosts = getBoosts(stack, pokemon)
        if (boosts.isEmpty()) {
            return InteractionResultHolder.fail(stack)
        }

        if (!canUseOnPokemon(stack, pokemon)) {
            return InteractionResultHolder.fail(stack)
        }

        boosts.forEach { (stat, value) ->
            pokemon.addRideBoost(stat, value)
        }

        pokemon.entity?.playSound(CobblemonSounds.BERRY_EAT, 1F, 1F)
        if (!player.isCreative) {
            stack.shrink(1)
        }

        return InteractionResultHolder.success(stack)
    }

    fun calculateRidingBoostForFlavour(flavour: Flavour, apricorn: Apricorn, value: Int, nature: Nature): Float {
        val tasteMultiplier = if (flavour == nature.dislikedFlavour) {
            DISLIKED_FLAVOUR_MULTIPLIER
        } else if (flavour == nature.favouriteFlavour) {
            LIKED_FLAVOUR_MULTIPLIER
        } else {
            1F
        }

        val apricornPolarity = apricorn.flavourStrength[flavour]
        val apricornMultiplier = if (apricornPolarity == true) {
            STRONG_APRICORN_MULTIPLIER
        } else if (apricornPolarity == false) {
            WEAK_APRICORN_MULTIPLIER
        } else {
            1F
        }

        return value * apricornMultiplier * tasteMultiplier
    }

    override fun use(world: Level, user: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        if (world is ServerLevel && user is ServerPlayer) {
            val stack = user.getItemInHand(hand)
            return use(user, stack)
        }
        return InteractionResultHolder.success(user.getItemInHand(hand))
    }
}
