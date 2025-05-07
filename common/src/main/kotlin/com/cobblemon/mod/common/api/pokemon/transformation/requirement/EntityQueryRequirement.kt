package com.cobblemon.mod.common.api.pokemon.transformation.requirement

import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.world.entity.LivingEntity

/**
 * A [TransformationRequirement] that expects a [net.minecraft.world.entity.LivingEntity] to be attached to the [com.cobblemon.mod.common.pokemon.Pokemon].
 * It can be the [com.cobblemon.mod.common.entity.pokemon.PokemonEntity] if present or the [net.minecraft.world.entity.LivingEntity] that owns it.
 *
 * Whenever an entity can't be resolved [TransformationRequirement.check] will never succeed.
 *
 * @author Licious
 * @since March 21st, 2022
 */
interface EntityQueryRequirement : TransformationRequirement {
    override fun check(pokemon: Pokemon): Boolean {
        val queriedEntity = pokemon.entity ?: pokemon.getOwnerPlayer() ?: return false
        return this.check(pokemon, queriedEntity)
    }

    /**
     * Checks if the given [Pokemon] & [net.minecraft.world.entity.LivingEntity] satisfies the requirement.
     *
     * @param pokemon The [Pokemon] being queried.
     * @param queriedEntity The [net.minecraft.world.entity.LivingEntity] that can be compared in context of a [Level].
     * @return If the requirement was satisfied.
     */
    fun check(pokemon: Pokemon, queriedEntity: LivingEntity): Boolean
}