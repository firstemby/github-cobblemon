import EntityQueryRequirement.EntityQueryRequirement
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level

/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

<<<<<<<< HEAD:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/transformation/requirements/WorldRequirement.kt
package com.cobblemon.mod.common.pokemon.transformation.requirements
========
package com.cobblemon.mod.common.pokemon.requirements
>>>>>>>> origin/main:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/requirements/WorldRequirement.kt

import com.cobblemon.mod.common.api.pokemon.requirement.EntityQueryRequirement
import com.cobblemon.mod.common.pokemon.Pokemon
<<<<<<<< HEAD:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/transformation/requirements/WorldRequirement.kt
import com.cobblemon.mod.common.pokemon.transformation.requirements.template.EntityQueryRequirement
========
>>>>>>>> origin/main:common/src/main/kotlin/com/cobblemon/mod/common/pokemon/requirements/WorldRequirement.kt
import net.minecraft.world.entity.LivingEntity
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level

/**
 * A [EntityQueryRequirement] for when a [Pokemon] is expected to be in a [Level].
 *
 * @property identifier The [ResourceLocation] of the [Level] the queried entity is expected to be in.
 *
 * @author Licious
 * @since March 21st, 2022
 */
class WorldRequirement(val identifier: ResourceLocation = ResourceLocation.parse("minecraft:the_overworld")) : EntityQueryRequirement {
    companion object {
        const val ADAPTER_VARIANT = "world"
    }

    override fun check(pokemon: Pokemon, queriedEntity: LivingEntity) = queriedEntity.level().dimension().location() == this.identifier
}