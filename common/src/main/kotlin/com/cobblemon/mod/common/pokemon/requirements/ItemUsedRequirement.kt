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
