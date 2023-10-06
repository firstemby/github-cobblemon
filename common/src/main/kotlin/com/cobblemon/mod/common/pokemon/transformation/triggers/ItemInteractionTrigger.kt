/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.triggers

import com.cobblemon.mod.common.api.pokemon.transformation.trigger.ContextTrigger
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.predicate.NbtItemPredicate
import com.cobblemon.mod.common.registry.ItemIdentifierCondition
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * Represents a [ContextTrigger] with [NbtItemPredicate] context.
 * These are triggered upon interaction with any [ItemStack] that matches the given predicate.
 *
 * @property requiredContext The [NbtItemPredicate] expected to match.
 * @author Licious
 * @since March 20th, 2022
 */
open class ItemInteractionTrigger(
    override val requiredContext: NbtItemPredicate =
        NbtItemPredicate(ItemIdentifierCondition(ResourceLocation.fromNamespaceAndPath("minecraft", "fish")), null),
) : ContextTrigger<ItemInteractionTrigger.ItemInteractionContext, NbtItemPredicate> {

    override fun testContext(pokemon: Pokemon, context: ItemInteractionContext): Boolean =
        this.requiredContext.item.fits(context.stack.item, context.world.registryAccess().registryOrThrow(Registries.ITEM)) &&
        this.requiredContext.nbt?.matches(context.stack) ?: true

    data class ItemInteractionContext(
        val stack: ItemStack,
        val world: Level
    )

    companion object {
        const val ADAPTER_VARIANT = "item_interact"
    }
}