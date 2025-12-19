package com.cobblemon.mod.common.pokemon.requirements

import com.cobblemon.mod.common.api.pokemon.requirement.InteractionAttemptContext
import com.cobblemon.mod.common.api.pokemon.requirement.InteractionRequirement

class IsOwnerRequirement : InteractionRequirement {

    override fun check(context: InteractionAttemptContext): Boolean {
        val player = context.player ?: return false
        return context.pokemon.getOwnerUUID() == player.uuid
    }

    companion object {
        const val ADAPTER_VARIANT = "is_owner"
    }
}
