package com.cobblemon.mod.common.api.pokemon.requirement

import com.cobblemon.mod.common.api.interaction.InteractionAttemptContext

fun Requirement.isSatisfied(context: InteractionAttemptContext): Boolean {
    return when (this) {
        is InteractionRequirement -> this.check(context)
        else -> this.check(context.pokemon)
    }
}