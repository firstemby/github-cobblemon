import EntityQueryRequirement.EntityQueryRequirement
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

<<<<<<<< HEAD:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/transformation/requirements/AreaRequirement.kt
package com.cobblemon.mod.common.pokemon.transformation.requirements
========
package com.cobblemon.mod.common.pokemon.requirements
>>>>>>>> origin/main:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/requirements/AreaRequirement.kt

import com.cobblemon.mod.common.api.pokemon.requirement.EntityQueryRequirement
import com.cobblemon.mod.common.pokemon.Pokemon
<<<<<<<< HEAD:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/transformation/requirements/AreaRequirement.kt
import com.cobblemon.mod.common.pokemon.transformation.requirements.template.EntityQueryRequirement
========
>>>>>>>> origin/main:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/requirements/AreaRequirement.kt
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * A [EntityQueryRequirement] for when a [Pokemon] is expected to be in a certain area.
 *
 * @property box The [AABB] expected to be in.
<<<<<<<< HEAD:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/transformation/requirements/AreaRequirement.kt
 *
========
>>>>>>>> origin/main:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/requirements/AreaRequirement.kt
 * @author Licious
 * @since March 21st, 2022
 */
class AreaRequirement(val box: AABB = AABB.unitCubeFromLowerCorner(Vec3.ZERO)) : EntityQueryRequirement {
    companion object {
        const val ADAPTER_VARIANT = "area"
    }

    override fun check(pokemon: Pokemon, queriedEntity: LivingEntity) = box.contains(queriedEntity.position())
}