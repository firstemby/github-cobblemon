/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molang.function

import com.bedrockk.molang.runtime.MoParams
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.MoValue
import com.cobblemon.mod.common.api.abilities.AbilityPool
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.resolveFromObjectOrString

object AbilityPoolMoLangFunctions : AbstractMoLangFunctionHolder<AbilityPool>() {
    override fun AbilityPool.moLangFunctions(): MutableMap<String, (MoParams) -> Any> {
        val abilityPool = this
        return mutableMapOf(
            "select" to select@{ params ->
                val species = params.get<MoValue>(0).resolveFromObjectOrString(PokemonSpecies::getByName)
                    ?: return@select DoubleValue.ZERO
                return@select ObjectValue(abilityPool.select(species, emptySet()))
            },
            "inherited_ability" to inherited_ability@{ params ->
                val pokemon = params.get<ObjectValue<Pokemon>>(0).obj
                val ability = abilityPool.getInheritedAbility(pokemon)
                    ?: return@inherited_ability DoubleValue.ZERO
                return@inherited_ability ObjectValue(ability)
            }
        )
    }
}
