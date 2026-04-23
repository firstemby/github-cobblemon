/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.item.components.PrintedPhotoComponent
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.MapItem
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level

class PrintedPhotoItem(properties: Item.Properties) : MapItem(properties) {
    override fun inventoryTick(stack: ItemStack, level: Level, entity: Entity, slotId: Int, isSelected: Boolean) {
        super.inventoryTick(stack, level, entity, slotId, isSelected)

        if (level.isClientSide) return
        val player = entity as? ServerPlayer ?: return
        if (player.tickCount % 20 != 0) return

        val printedPhoto = PrintedPhotoComponent.get(stack) ?: return
        if (printedPhoto.detectedSpecies.isEmpty()) return

        val pokedex = Cobblemon.playerDataManager.getPokedexData(player.uuid)
        printedPhoto.detectedSpecies.forEach { speciesKey ->
            val speciesId = ResourceLocation.tryParse(speciesKey) ?: return@forEach
            if (pokedex.getKnowledgeForSpecies(speciesId) >= PokedexEntryProgress.ENCOUNTERED) return@forEach
            val species = PokemonSpecies.getByIdentifier(speciesId) ?: return@forEach
            pokedex.encounter(species.create(level = 1))
        }
    }

    override fun appendHoverText(
        stack: ItemStack,
        context: Item.TooltipContext,
        tooltipComponents: MutableList<Component>,
        tooltipFlag: TooltipFlag
    ) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag)

        val printedPhoto = PrintedPhotoComponent.get(stack) ?: return
        tooltipComponents.add(Component.translatable("item.cobblemon.printed_photo.photo_id", printedPhoto.photoId))
    }
}

