/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.interactive

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.pokemon.interaction.ExperienceCandyUseEvent
import com.cobblemon.mod.common.api.pokemon.experience.CandyExperienceSource
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.item.CobblemonItem
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.food.FoodProperties
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Rarity
import net.minecraft.world.level.Level

/**
 * An experience candy item.
 * See Bulbapedias [RareCandy](https://bulbapedia.bulbagarden.net/wiki/Rare_Candy) & [ExperienceCandy](https://bulbapedia.bulbagarden.net/wiki/Exp._Candy) articles.
 *
 * @property calculator The [Calculator] that will resolve the amount of experience to give.
 *
 * @author Licious
 * @since May 5th, 2022
 */
class CandyItem(
    val item_rarity: Rarity,
    val expYield: (ServerPlayer, Pokemon?) -> Int
) : CobblemonItem(Properties().apply {
    rarity(item_rarity)
    food(FoodProperties.Builder()
        .alwaysEdible()
        .nutrition(0)
        .saturationModifier(0.0f)
        .build())
}) {

    override fun finishUsingItem(stack: ItemStack, level: Level, livingEntity: LivingEntity): ItemStack? {
        if (livingEntity !is Player) return super.finishUsingItem(stack, level, livingEntity)

        /* Didn't find a good way to determine the XP needed until the beginning of the next level.
         * This works but is a little jank. Feel free to investigate a better solution.
         */
        if (level.isClientSide && this == CobblemonItems.RARE_CANDY) {
            val volume = if (livingEntity.experienceLevel > 30) 1.0f else livingEntity.experienceLevel.toFloat() / 30.0f
            livingEntity.playSound(SoundEvents.PLAYER_LEVELUP, volume * 0.75f, 1.0f)
        }

        if (livingEntity !is ServerPlayer) return super.finishUsingItem(stack, level, livingEntity)
        if (this == CobblemonItems.RARE_CANDY) {
            livingEntity.setExperienceLevels(livingEntity.experienceLevel + 1)
            livingEntity.setExperiencePoints(0)
        }
        else {
            val expYield = expYield(livingEntity, null)
            val playerExperience = (expYield / Cobblemon.config.mobExperienceConversionRate).toInt().coerceAtLeast(1)
            ExperienceOrb.award(livingEntity.serverLevel(), livingEntity.position(), playerExperience)
        }
        return super.finishUsingItem(stack, level, livingEntity)
    }

    override fun interactLivingEntity(
        stack: ItemStack,
        player: Player,
        interactionTarget: LivingEntity,
        usedHand: InteractionHand
    ): InteractionResult {
        if (player.level().isClientSide) return InteractionResult.FAIL
        if (player.isCrouching) return InteractionResult.FAIL
        if (interactionTarget !is PokemonEntity) return InteractionResult.PASS
        if (interactionTarget.pokemon.getOwnerPlayer() != player) return InteractionResult.PASS
        return applyToPokemon(player as ServerPlayer, stack, interactionTarget.pokemon)?.result ?: InteractionResult.FAIL
    }

    fun applyToPokemon(player: ServerPlayer, stack: ItemStack, pokemon: Pokemon): InteractionResultHolder<ItemStack>? {
        if (!pokemon.canLevelUpFurther()) {
            return InteractionResultHolder.fail(stack)
        }

        val experience = expYield(player, pokemon)
        CobblemonEvents.EXPERIENCE_CANDY_USE_PRE.postThen(
                event = ExperienceCandyUseEvent.Pre(player, pokemon, this, experience, experience),
                ifSucceeded = { preEvent ->
                    val finalExperience = preEvent.experienceYield
                    val source = CandyExperienceSource(player, stack)
                    val result = pokemon.addExperienceWithPlayer(player, source, finalExperience)
                    // We do this just so we can post the event once the item has been consumed if needed instead of repeating the even post
                    var returnValue = false
                    if (result.experienceAdded > 0) {
                        stack.consume(1, player)
                        returnValue = true
                    }
                    pokemon.entity?.playSound(CobblemonSounds.MEDICINE_CANDY_USE, 1F, 1F)
                    CobblemonEvents.EXPERIENCE_CANDY_USE_POST.post(ExperienceCandyUseEvent.Post(player, pokemon, this, result))

                    return if (returnValue)
                        InteractionResultHolder.success(stack)
                    else
                        InteractionResultHolder.fail(stack)
                }
        )
        return InteractionResultHolder.fail(stack)
    }

    companion object {
        const val DEFAULT_XS_CANDY_YIELD = 100
        const val DEFAULT_S_CANDY_YIELD = 800
        const val DEFAULT_M_CANDY_YIELD = 3000
        const val DEFAULT_L_CANDY_YIELD = 10000
        const val DEFAULT_XL_CANDY_YIELD = 30000
    }
}