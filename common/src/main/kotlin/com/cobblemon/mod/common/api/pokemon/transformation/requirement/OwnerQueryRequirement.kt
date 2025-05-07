package com.cobblemon.mod.common.api.pokemon.transformation.requirement

import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity

/**
 * A [TransformationRequirement] that expects a [net.minecraft.world.entity.LivingEntity] to be attached to the [com.cobblemon.mod.common.pokemon.Pokemon].
 * It can be the [com.cobblemon.mod.common.entity.pokemon.PokemonEntity] if present or the [net.minecraft.world.entity.LivingEntity] that owns it.
 *
 * Whenever an entity can't be resolved [TransformationRequirement.check] will never succeed.
 *
 * @author Polymeta
 * @since May 6th, 2025
 */
interface OwnerQueryRequirement : TransformationRequirement {
    override fun check(pokemon: Pokemon): Boolean {
        val owner = pokemon.getOwnerEntity() ?: return false
        return when (owner) {
            is ServerPlayer -> {
                this.checkPlayer(pokemon, owner)
            }
            is NPCEntity -> {
                this.checkNPC(pokemon, owner)
            }
            else -> false
        }
    }

    /**
     * Checks if the given [Pokemon] & [ServerPlayer] satisfies the requirement.
     *
     * @param pokemon The [Pokemon] being queried.
     * @param owner The [ServerPlayer] that owns this [Pokemon].
     * @return If the requirement was satisfied.
     */
    fun checkPlayer(pokemon: Pokemon, owner: ServerPlayer): Boolean

    /**
     * Checks if the given [Pokemon] & [NPCEntity] satisfies the requirement.
     * This method mainly exists as NPCs are different to players in a lot of ways (e.g., lack of advancements),
     * so they need to be handled differently, either by checking an NPC config or always returning true/false
     *
     * @param pokemon The [Pokemon] being queried.
     * @param owner The [NPCEntity] that owns this [Pokemon].
     * @return If the requirement was satisfied.
     */
    fun checkNPC(pokemon: Pokemon, owner: NPCEntity): Boolean
}