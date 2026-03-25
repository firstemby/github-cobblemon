/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.interactive

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.interaction.PokemonEntityInteraction
import com.cobblemon.mod.common.api.moves.BenchedMove
import com.cobblemon.mod.common.api.text.gray
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.item.CobblemonItem
import com.cobblemon.mod.common.item.components.TMMoveComponent
import com.cobblemon.mod.common.util.lang
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag

class TechnicalMachineItem(properties: Properties) : CobblemonItem(properties), PokemonEntityInteraction {
    override val accepted: Set<PokemonEntityInteraction.Ownership> = setOf(PokemonEntityInteraction.Ownership.OWNER)

    override fun processInteraction(player: ServerPlayer, entity: PokemonEntity, stack: ItemStack): Boolean {
        val moveTemplate = TMMoveComponent.getTMMove(stack) ?: return false
        val pokemon = entity.pokemon

        val tmLearnableMoves = pokemon.form.moves.tmLearnableMoves()

        if (moveTemplate !in tmLearnableMoves) {
            player.displayClientMessage(lang("tms.cannot_learn", pokemon.getDisplayName(), moveTemplate.displayName), true)
            return false
        }
        if (pokemon.moveSet.getMoveTemplates().contains(moveTemplate) || pokemon.allAccessibleMoves.contains(moveTemplate)) {
            player.displayClientMessage(lang("tms.already_known", pokemon.getDisplayName(), moveTemplate.displayName), true)
            return false
        }

        if (!player.isCreative && !Cobblemon.config.infiniteTmUses) stack.shrink(1)

        if (pokemon.moveSet.hasSpace()) {
            pokemon.moveSet.add(moveTemplate.create())
        } else {
            pokemon.benchedMoves.add(BenchedMove(moveTemplate, 0))
        }

        player.displayClientMessage(lang("tms.teach_move", pokemon.getDisplayName(), moveTemplate.displayName).green(), true)
        player.level().playSound(null, player.blockPosition(), CobblemonSounds.TM_USE, SoundSource.PLAYERS, 1.0F, 1.0F)
        entity.cry()
        return true
    }

    override fun appendHoverText(stack: ItemStack, context: TooltipContext, tooltip: MutableList<Component>, tooltipFlag: TooltipFlag) {
        val move = TMMoveComponent.getTMMove(stack)
        val text = move?.displayName ?: lang("tms.unknown_move")
        tooltip.add(text.gray())
        super.appendHoverText(stack, context, tooltip, tooltipFlag)
    }
}
