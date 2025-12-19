package com.cobblemon.mod.common.pokemon.requirements

import com.cobblemon.mod.common.api.interaction.InteractionAttemptContext
import com.cobblemon.mod.common.api.pokemon.requirement.InteractionRequirement
import com.cobblemon.mod.common.pokemon.Pokemon

class IsOwnerRequirement : InteractionRequirement {

    override fun check(context: InteractionAttemptContext): Boolean {
        val player = context.player ?: return false
        return context.pokemon.getOwnerUUID() == player.uuid
    }

    override fun check(pokemon: Pokemon): Boolean {
        throw UnsupportedOperationException(
            "ItemUsedRequirement is interaction-only and cannot be evaluated without context"
        )
    }

    companion object {
        const val ADAPTER_VARIANT = "is_owner"
    }
}
