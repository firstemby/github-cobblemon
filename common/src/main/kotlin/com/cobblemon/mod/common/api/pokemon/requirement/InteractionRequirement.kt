package com.cobblemon.mod.common.api.pokemon.requirement

import com.cobblemon.mod.common.api.interaction.InteractionAttemptContext

interface InteractionRequirement : Requirement {
    fun check(context: InteractionAttemptContext): Boolean
}