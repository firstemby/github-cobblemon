/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.requirements

import com.cobblemon.mod.common.api.pokemon.transformation.Transformation
import com.cobblemon.mod.common.api.pokemon.transformation.requirement.TransformationRequirement
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.predicate.NbtItemPredicate
import com.cobblemon.mod.common.registry.ItemIdentifierCondition
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

/**
 * A [TransformationRequirement] for a [Pokemon.heldItem]. Satisfied if a Pokemon is holding the [itemCondition].
 *
 * @property itemCondition The [NbtItemPredicate] expected to match the [Pokemon.heldItem].
 * @property consumeHeldItem If the [Transformation] will consume the [Pokemon.heldItem].
 *
 * @author Licious
 * @since March 21st, 2022
 */
class HeldItemRequirement(
    val itemCondition: NbtItemPredicate =
        NbtItemPredicate(ItemIdentifierCondition(ResourceLocation.fromNamespaceAndPath("minecraft", "air"))),
    val consumeHeldItem: Boolean? = null
) : TransformationRequirement {

    override fun check(pokemon: Pokemon): Boolean = this.itemCondition.test(pokemon.heldItemNoCopy())

    override fun fulfill(pokemon: Pokemon) {
        if (this.consumeHeldItem == true) pokemon.swapHeldItem(ItemStack.EMPTY)
        super.fulfill(pokemon)
    }

    companion object {
        const val ADAPTER_VARIANT = "held_item"
    }
}