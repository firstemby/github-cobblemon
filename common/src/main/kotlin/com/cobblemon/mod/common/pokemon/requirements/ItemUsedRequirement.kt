package com.cobblemon.mod.common.pokemon.requirements

import com.cobblemon.mod.common.api.interaction.InteractionAttemptContext
import com.cobblemon.mod.common.api.pokemon.requirement.InteractionRequirement
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.advancements.critereon.ItemPredicate

class ItemUsedRequirement(
    private val itemCondition: ItemPredicate
) : InteractionRequirement {

    override fun check(context: InteractionAttemptContext): Boolean {
        return itemCondition.test(context.item)
    }

    override fun check(pokemon: Pokemon): Boolean {
        throw UnsupportedOperationException(
            "ItemUsedRequirement is interaction-only and cannot be evaluated without context"
        )
    }

    companion object {
        const val ADAPTER_VARIANT = "item_used"
    }
}
