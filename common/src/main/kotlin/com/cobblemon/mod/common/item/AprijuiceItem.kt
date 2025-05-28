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
import com.cobblemon.mod.common.client.pot.CookingQuality
import com.cobblemon.mod.common.pokemon.Nature
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Component.translatable
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.UseAnim
import net.minecraft.world.level.Level

class AprijuiceItem(val type: Apricorn): CobblemonItem(Properties().stacksTo(16)), PokemonSelectingItem {
    override val bagItem = null

    companion object {
        const val DISLIKED_FLAVOUR_MULTIPLIER = 0.75F
        const val LIKED_FLAVOUR_MULTIPLIER = 1.25F

        const val STRONG_APRICORN_MULTIPLIER = 1.25F
        const val WEAK_APRICORN_MULTIPLIER = 0.75F
    }

    override fun use(world: Level, user: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = user.getItemInHand(hand)

        val hasFlavour = stack.get(CobblemonItemComponents.FLAVOUR)?.flavours?.any { it.value > 0 } == true
        return if (!hasFlavour) {
            // act like a drink :D
            user.startUsingItem(hand)
            InteractionResultHolder.consume(stack)
        } else {
            if (world is ServerLevel && user is ServerPlayer) {
                return super<PokemonSelectingItem>.use(user, stack)
            }
            InteractionResultHolder.pass(stack)
        }
    }

    override fun getName(stack: ItemStack): Component {
        val flavourComponent = stack.get(CobblemonItemComponents.FLAVOUR)
        val hasFlavour = flavourComponent?.flavours?.values?.any { it > 0 } == true
        val quality = flavourComponent?.getQuality()

        val baseNameKey = "item.cobblemon.aprijuice_${type.name.lowercase()}"
        val prefixKey = when {
            hasFlavour && quality == CookingQuality.HIGH -> "item.cobblemon.aprijuice.prefix.delicious"
            hasFlavour && quality == CookingQuality.MEDIUM -> "item.cobblemon.aprijuice.prefix.tasty"
            !hasFlavour -> "item.cobblemon.aprijuice.prefix.plain"
            else -> null
        }

        return if (prefixKey != null) {
            Component.translatable("item.cobblemon.aprijuice.quality_format",
                Component.translatable(prefixKey),
                Component.translatable(baseNameKey)
            )
        } else {
            Component.translatable(baseNameKey)
        } // todo if no flavor call it "Plain Red Aprijuice" or "Raw Red Aprijuice" maybe and have players able to drink it
    }

    override fun canUseOnPokemon(stack: ItemStack, pokemon: Pokemon): Boolean {
        val boosts = getBoosts(stack, pokemon)
        return boosts.isNotEmpty() && boosts.any { pokemon.canAddRideBoost(it.key, it.value) } && super.canUseOnPokemon(stack, pokemon)
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
        if (!canUseOnPokemon(stack, pokemon)) {
            return InteractionResultHolder.fail(stack)
        }
        val boosts = getBoosts(stack, pokemon)
        // Feed the Pokémon 1 fullness point
        pokemon.feedPokemon(1)

        boosts.forEach { (stat, value) ->
            pokemon.addRideBoost(stat, value)
        }

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

    override fun finishUsingItem(stack: ItemStack, world: Level, user: LivingEntity): ItemStack {
        val hasFlavour = stack.get(CobblemonItemComponents.FLAVOUR)?.flavours?.any { it.value > 0 } == true

        if (!hasFlavour && user is Player && !world.isClientSide) {
            user.foodData.eat(1, 0.1f)
            if (!user.isCreative) {
                stack.shrink(1)
            }
        }

        return super.finishUsingItem(stack, world, user)
    }

    override fun getUseAnimation(stack: ItemStack): UseAnim {
        val hasFlavour = stack.get(CobblemonItemComponents.FLAVOUR)?.flavours?.any { it.value > 0 } == true
        return if (hasFlavour) UseAnim.NONE else UseAnim.DRINK
    }

    override fun getUseDuration(stack: ItemStack, entity: LivingEntity): Int {
        val hasFlavour = stack.get(CobblemonItemComponents.FLAVOUR)?.flavours?.any { it.value > 0 } == true
        return if (hasFlavour) 0 else 32 // 32 ticks like drinking a potion
    }

    // todo not sure which one is needed at the moment, but I assume just the eating sound?
    override fun getDrinkingSound(): SoundEvent {
        return SoundEvents.GENERIC_DRINK
    }

    override fun getEatingSound(): SoundEvent {
        return SoundEvents.GENERIC_DRINK
    }
}
