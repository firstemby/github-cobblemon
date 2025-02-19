/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.triggers

import com.cobblemon.mod.common.api.pokemon.transformation.trigger.ContextTrigger
import com.cobblemon.mod.common.api.pokemon.transformation.trigger.TriggerContext
import net.minecraft.advancements.critereon.ItemPredicate
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level

/**
 * Represents a [ContextTrigger] with [ItemPredicate] context.
 * These are triggered upon interaction with any [ItemStack] that matches the given predicate.
 *
 * @property requiredContext The [ItemPredicate] expected to match.
 * @author Licious
 * @since March 20th, 2022
 */
open class ItemInteractionTrigger(
    override val requiredContext: ItemPredicate = ItemPredicate.Builder.item().of(Items.EGG).build(),
) : ContextTrigger<ItemPredicate> {

    override fun testContext(context: TriggerContext) = context is Context && this.requiredContext.test(context.stack)

    data class Context(
        val stack: ItemStack,
        val world: Level
    ): TriggerContext

    companion object {
        const val ADAPTER_VARIANT = "item_interact"
    }
}