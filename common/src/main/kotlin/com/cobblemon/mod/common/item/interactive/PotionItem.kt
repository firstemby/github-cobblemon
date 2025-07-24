/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.interactive

import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.pokemon.healing.PokemonHealedEvent
import com.cobblemon.mod.common.api.item.HealingSource
import com.cobblemon.mod.common.api.item.PokemonSelectingItem
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.item.CobblemonItem
import com.cobblemon.mod.common.item.battle.BagItem
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.genericRuntime
import com.cobblemon.mod.common.util.getBattleState
import com.cobblemon.mod.common.util.giveOrDropItemStack
import com.cobblemon.mod.common.util.resolveInt
import java.lang.Integer.min
import net.minecraft.world.entity.player.Player
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.*
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

class PotionItem(
    val type: PotionType,
    block: Block
) : ItemNameBlockItem(block, Properties().apply {
    when (type.name) {
        PotionType.MAX_POTION.name,
        PotionType.FULL_RESTORE.name -> rarity(Rarity.UNCOMMON)
    }
}), PokemonSelectingItem, HealingSource {

    override val bagItem = type
    override fun canUseOnPokemon(stack: ItemStack, pokemon: Pokemon) = !pokemon.isFullHealth() && pokemon.currentHealth > 0
    override fun use(world: Level, user: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = user.getItemInHand(hand)

        if (user is ServerPlayer) {
            // Only invoke PokemonSelectingItem menu if we are in battle
            if (user.getBattleState() != null) {
                val result = use(user, stack)
                if (result.result != InteractionResult.PASS)
                    return result
            }
        }

        return super<ItemNameBlockItem>.use(world, user, hand)
    }

    override fun interactLivingEntity(
        stack: ItemStack,
        player: Player,
        target: LivingEntity,
        hand: InteractionHand
    ): InteractionResult {
        if (player.level().isClientSide) return InteractionResult.PASS

        if (player is ServerPlayer && target is PokemonEntity) {
            val pokemon = target.pokemon
            if (canUseOnPokemon(stack, pokemon)) {
                val result = applyToPokemon(player, stack, pokemon)
                if (result?.result == InteractionResult.SUCCESS) {
                    return InteractionResult.SUCCESS
                }
            }
            return InteractionResult.FAIL
        }

        return InteractionResult.PASS
    }


    override fun applyToPokemon(
        player: ServerPlayer,
        stack: ItemStack,
        pokemon: Pokemon
    ): InteractionResultHolder<ItemStack>? {
        if (pokemon.isFullHealth()) {
            return InteractionResultHolder.fail(stack)
        }
        val potionHealAmount = genericRuntime.resolveInt(type.amountToHeal())
        var healthToRestore = potionHealAmount
        CobblemonEvents.POKEMON_HEALED.postThen(PokemonHealedEvent(pokemon, potionHealAmount, this), { cancelledEvent -> return InteractionResultHolder.fail(stack)}) { event ->
            healthToRestore = event.amount
        }
        pokemon.currentHealth = min(pokemon.currentHealth + healthToRestore, pokemon.maxHealth)
        if (type.curesStatus) {
            pokemon.status = null
        }
        pokemon.entity?.playSound(CobblemonSounds.MEDICINE_SPRAY_USE, 1F, 1F)
        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1)
            player.giveOrDropItemStack(ItemStack(Items.GLASS_BOTTLE))
        }
        return InteractionResultHolder.success(stack)
    }

    override fun applyToBattlePokemon(player: ServerPlayer, stack: ItemStack, battlePokemon: BattlePokemon) {
        super.applyToBattlePokemon(player, stack, battlePokemon)
        battlePokemon.entity?.playSound(CobblemonSounds.MEDICINE_SPRAY_USE, 1F, 1F)
    }
}

enum class PotionType(val amountToHeal: () -> ExpressionLike, val curesStatus: Boolean) : BagItem {
    POTION({ com.cobblemon.mod.common.CobblemonMechanics.potions.potionRestoreAmount }, false) {
        override val itemName = "item.cobblemon.potion"
        override val returnItem = Items.GLASS_BOTTLE
        override fun getShowdownInput(actor: BattleActor, battlePokemon: BattlePokemon, data: String?) = "potion ${genericRuntime.resolveInt(amountToHeal(), battlePokemon)}"
        override fun canUse(stack: ItemStack, battle: PokemonBattle, target: BattlePokemon) =  target.health < target.maxHealth && target.health > 0
    },
    SUPER_POTION({ com.cobblemon.mod.common.CobblemonMechanics.potions.superPotionRestoreAmount }, false) {
        override val itemName = "item.cobblemon.super_potion"
        override val returnItem = Items.GLASS_BOTTLE
        override fun getShowdownInput(actor: BattleActor, battlePokemon: BattlePokemon, data: String?) = "potion ${genericRuntime.resolveInt(amountToHeal(), battlePokemon)}"
        override fun canUse(stack: ItemStack, battle: PokemonBattle, target: BattlePokemon) =  target.health < target.maxHealth && target.health > 0
    },
    HYPER_POTION({ com.cobblemon.mod.common.CobblemonMechanics.potions.hyperPotionRestoreAmount }, false) {
        override val itemName = "item.cobblemon.hyper_potion"
        override val returnItem = Items.GLASS_BOTTLE
        override fun getShowdownInput(actor: BattleActor, battlePokemon: BattlePokemon, data: String?) = "potion ${genericRuntime.resolveInt(amountToHeal(), battlePokemon)}"
        override fun canUse(stack: ItemStack, battle: PokemonBattle, target: BattlePokemon) =  target.health < target.maxHealth && target.health > 0
    },
    MAX_POTION({ 999999.0.asExpressionLike() }, false) {
        override val itemName = "item.cobblemon.max_potion"
        override val returnItem = Items.GLASS_BOTTLE
        override fun getShowdownInput(actor: BattleActor, battlePokemon: BattlePokemon, data: String?) = "potion ${battlePokemon.maxHealth - battlePokemon.health}"
        override fun canUse(stack: ItemStack, battle: PokemonBattle, target: BattlePokemon) =  target.health < target.maxHealth && target.health > 0
    },
    FULL_RESTORE({ 999999.0.asExpressionLike() }, true) {
        override val itemName = "item.cobblemon.full_restore"
        override val returnItem = Items.GLASS_BOTTLE
        override fun getShowdownInput(actor: BattleActor, battlePokemon: BattlePokemon, data: String?) = "full_restore"
        override fun canUse(stack: ItemStack, battle: PokemonBattle, target: BattlePokemon) =  target.health < target.maxHealth && target.health > 0
    }
}
