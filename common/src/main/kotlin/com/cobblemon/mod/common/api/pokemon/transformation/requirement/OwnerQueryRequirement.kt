/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokemon.transformation.requirement

import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.server.level.ServerPlayer

/**
 * A [TransformationRequirement] that expects the [Pokemon] to have an owner.
 * It can be the [ServerPlayer] or the [NPCEntity] that owns it.
 *
 * Whenever an owner can't be resolved [TransformationRequirement.check] will never succeed.
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