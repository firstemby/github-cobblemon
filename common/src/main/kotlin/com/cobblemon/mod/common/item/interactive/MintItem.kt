/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.interactive

import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.item.PokemonSelectingItem
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.client.render.models.blockbench.animation.PrimaryAnimation
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockActiveAnimation
import com.cobblemon.mod.common.item.CobblemonItem
import com.cobblemon.mod.common.pokemon.Nature
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.stadium.StadiumClientManager
import com.cobblemon.mod.common.stadium.deserialize
import com.cobblemon.mod.common.util.*
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.level.Level

class MintItem(val nature: Nature) : CobblemonItem(Properties()), PokemonSelectingItem {

    override val bagItem = null
    override fun canUseOnPokemon(pokemon: Pokemon) = pokemon.effectiveNature != nature
    override fun applyToPokemon(
        player: ServerPlayer,
        stack: ItemStack,
        pokemon: Pokemon
    ): InteractionResultHolder<ItemStack> {
        return if (pokemon.effectiveNature != nature) {
            if (!player.isCreative) {
                stack.shrink(1)
            }
            pokemon.entity?.playSound(CobblemonSounds.MEDICINE_HERB_USE, 1F, 1F)
            pokemon.mintedNature = nature
            player.sendSystemMessage(lang("mint.interact", pokemon.getDisplayName(), stack.hoverName), true)
            InteractionResultHolder.success(stack)
        } else {
            player.sendSystemMessage(lang("mint.same_nature", pokemon.getDisplayName(), stack.hoverName), true)
            InteractionResultHolder.fail(stack)
        }
    }

    override fun use(world: Level, user: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
//        if (user is ServerPlayer) {
//            return use(user, user.getItemInHand(hand))
//        }
        if (!world.isClientSide) return InteractionResultHolder.fail(user.getItemInHand(hand))
        val pokemon = CobblemonClient.storage.myParty.first { it?.entity != null } ?: return InteractionResultHolder.fail(user.getItemInHand(hand))
        val entity = pokemon.entity ?: return InteractionResultHolder.fail(user.getItemInHand(hand))
        val delegate = entity.delegate as? PokemonClientDelegate ?: return InteractionResultHolder.fail(user.getItemInHand(hand))
        val animation = deserialize(
            """
{
	"length": 3,
	"origin": {
		"position": [1.8317, 9.8673, -29.408],
		"rotation": [-172.81662, 47.964, 180]
	},
	"timeline": {
		"position": {
			"0.0": [0, 0, 0],
			"0.91667": [20, 0, 16]
		},
		"rotation": {
			"0.0": [0, 0, 0],
			"0.91667": [0, 35, 0]
		}
	}
}
                """.trimIndent()
        )
        val bedrockAnimation = (delegate.runtime.resolve("q.bedrock_primary('tandemaus', 'faint')".asExpression()).value() as ObjectValue<*>).obj as PrimaryAnimation
        delegate.addPrimaryAnimation(bedrockAnimation)
        StadiumClientManager.playAnimation(animation, entity)
        return InteractionResultHolder.success(user.getItemInHand(hand))
    }
}
