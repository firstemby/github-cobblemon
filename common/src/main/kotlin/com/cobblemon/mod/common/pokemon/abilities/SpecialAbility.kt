package com.cobblemon.mod.common.pokemon.abilities

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.abilities.*
import com.google.gson.JsonElement

/**
 * The [PotentialAbilityType] for a [SpecialAbility].
 *
 * @author Segfault Guy
 * @since February 10th, 2025
 */
object SpecialAbilityType : PotentialAbilityType<SpecialAbility> {

    const val KEY = "s:"

    override fun parseFromJSON(element: JsonElement): SpecialAbility? {
        val str = if (element.isJsonPrimitive) element.asString else return null
        val abilityString = str.substringAfter(KEY)
        try {
            val template = Abilities.getOrException(abilityString)
            return SpecialAbility(template)
        } catch (e: IllegalArgumentException) {
            Cobblemon.LOGGER.error(e.message)
            return null
        }
    }
}

/**
 * An ability that is not traditionally available for a species, but only for exclusive variations.
 *
 * @author Segfault Guy
 * @since February 10th, 2025
 */
class SpecialAbility(override val template: AbilityTemplate) : PotentialAbility {
    override val priority: Priority = Priority.NORMAL
    override val type = SpecialAbilityType
    override fun isSatisfiedBy(aspects: Set<String>) = false
}