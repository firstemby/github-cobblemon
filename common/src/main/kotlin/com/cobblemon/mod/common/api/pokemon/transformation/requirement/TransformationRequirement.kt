import com.cobblemon.mod.common.api.pokemon.transformation.Transformation
import com.cobblemon.mod.common.pokemon.Pokemon

/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

<<<<<<<< HEAD:common/src/main/kotlin/com/cobblemon/mod/common/api/pokemon/transformation/requirement/TransformationRequirement.kt
package com.cobblemon.mod.common.api.pokemon.transformation.requirement
========
package com.cobblemon.mod.common.api.pokemon.requirement
>>>>>>>> origin/main:common/src/main/kotlin/com/cobblemon/mod/common/api/pokemon/requirement/Requirement.kt

import com.cobblemon.mod.common.api.pokemon.transformation.Transformation
import com.cobblemon.mod.common.api.pokemon.transformation.trigger.ContextTrigger
import com.cobblemon.mod.common.pokemon.Pokemon

/**
<<<<<<<< HEAD:common/src/main/kotlin/com/cobblemon/mod/common/api/pokemon/transformation/requirement/TransformationRequirement.kt
 * Represents a requirement for a transformation.
========
 * Represents a universal requirement suitable for various checks on pokemon..
>>>>>>>> origin/main:common/src/main/kotlin/com/cobblemon/mod/common/api/pokemon/requirement/Requirement.kt
 * Requirements are constant and will never change regardless of the backing action.
 *
<<<<<<<< HEAD:common/src/main/kotlin/com/cobblemon/mod/common/api/pokemon/transformation/requirement/TransformationRequirement.kt
 * See [Transformation.requirements] & [Transformation.test] for usage.
========
 * See [Evolution.requirements] & [Evolution.test] & [com.cobblemon.mod.common.api.interaction.PokemonInteractions.findInteraction] for usages.
>>>>>>>> origin/main:common/src/main/kotlin/com/cobblemon/mod/common/api/pokemon/requirement/Requirement.kt
 *
 * @author Licious
 * @since March 19th, 2022
 */
<<<<<<<< HEAD:common/src/main/kotlin/com/cobblemon/mod/common/api/pokemon/transformation/requirement/TransformationRequirement.kt
fun interface TransformationRequirement {
========
fun interface Requirement {
>>>>>>>> origin/main:common/src/main/kotlin/com/cobblemon/mod/common/api/pokemon/requirement/Requirement.kt

    /**
     * Checks if the given [Pokemon] satisfies the requirement.
     *
     * @param pokemon The [Pokemon] being queried.
     * @return If the requirement was satisfied.
     */
    fun check(pokemon: Pokemon): Boolean

    /**
     * Callback after the [Transformation] has been performed.
     *
     * @param pokemon The [Pokemon] being queried.
     */
    fun fulfill(pokemon: Pokemon) {}
}