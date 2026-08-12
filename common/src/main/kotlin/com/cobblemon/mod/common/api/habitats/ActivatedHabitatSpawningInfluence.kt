/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.habitats

import com.cobblemon.mod.common.api.habitats.spawningstyle.ActivatedHabitatSpawning
import com.cobblemon.mod.common.api.spawning.detail.SpawnAction
import com.cobblemon.mod.common.api.spawning.influence.SpawningInfluence
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.world.entity.Entity

/**
 * Influence added to the activated habitat spawner blocks so that they can track the entity and apply block modifiers.
 *
 * @author Hiroku
 * @since February 15th, 2026
 */
class ActivatedHabitatSpawningInfluence(val activatedHabitatSpawning: ActivatedHabitatSpawning) : SpawningInfluence {
    override fun affectSpawn(action: SpawnAction<*>, entity: Entity) {
        if (entity is PokemonEntity) {
            activatedHabitatSpawning.habitatBlockEntity.modifiers.apply(entity)
            activatedHabitatSpawning.habitatBlockEntity.spawnedEntityIDs.add(entity.id)
        }
    }
}