/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

<<<<<<<< HEAD:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/transformation/requirements/template/EntityQueryRequirement.kt
package com.cobblemon.mod.common.pokemon.transformation.requirements.template

import com.cobblemon.mod.common.api.pokemon.transformation.requirement.TransformationRequirement
========
package com.cobblemon.mod.common.api.pokemon.requirement

>>>>>>>> origin/main:common/src/main/kotlin/com/cobblemon/mod/common/api/pokemon/requirement/EntityQueryRequirement.kt
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.world.entity.LivingEntity

/**
<<<<<<<< HEAD:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/transformation/requirements/template/EntityQueryRequirement.kt
 * A [TransformationRequirement] that expects a [LivingEntity] to be attached to the [Pokemon].
 * It can be the [PokemonEntity] if present or the [LivingEntity] that owns it.
 *
 * Whenever an entity can't be resolved [TransformationRequirement.check] will never succeed.
========
 * An [Requirement] that expects a [LivingEntity] to be attached to the [Pokemon].
 * It can be the [PokemonEntity] if present or the [LivingEntity] that owns it.
 *
 * Whenever an entity can't be resolved [Requirement.check] will never succeed.
>>>>>>>> origin/main:common/src/main/kotlin/com/cobblemon/mod/common/api/pokemon/requirement/EntityQueryRequirement.kt
 *
 * @author Licious
 * @since March 21st, 2022
 */
<<<<<<<< HEAD:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/transformation/requirements/template/EntityQueryRequirement.kt
interface EntityQueryRequirement : TransformationRequirement {
========
interface EntityQueryRequirement : Requirement {
>>>>>>>> origin/main:common/src/main/kotlin/com/cobblemon/mod/common/api/pokemon/requirement/EntityQueryRequirement.kt
    override fun check(pokemon: Pokemon): Boolean {
        val queriedEntity = pokemon.entity ?: pokemon.getOwnerPlayer() ?: return false
        return this.check(pokemon, queriedEntity)
    }

    /**
     * Checks if the given [Pokemon] & [LivingEntity] satisfies the requirement.
     *
     * @param pokemon The [Pokemon] being queried.
     * @param queriedEntity The [LivingEntity] that can be compared in context of a [Level].
     * @return If the requirement was satisfied.
     */
    fun check(pokemon: Pokemon, queriedEntity: LivingEntity): Boolean
}