/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.requirements

import com.cobblemon.mod.common.api.pokemon.requirement.InteractionAttemptContext
import com.cobblemon.mod.common.api.pokemon.requirement.InteractionRequirement
import net.minecraft.advancements.critereon.ItemPredicate

class ItemUsedRequirement(
    private val itemCondition: ItemPredicate
) : InteractionRequirement {

    override fun check(context: InteractionAttemptContext): Boolean {
        return itemCondition.test(context.item)
    }

    companion object {
        const val ADAPTER_VARIANT = "item_used"
    }
}
