/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.nest

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.Cobblemon.LOGGER
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.abilities.Abilities
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.fishing.BobberSpawnPokemonEvent
import com.cobblemon.mod.common.api.fishing.FishingBait
import com.cobblemon.mod.common.api.pokemon.Natures
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.egg.EggGroup
import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.spawning.SpawnBucket
import com.cobblemon.mod.common.api.spawning.SpawnCause
import com.cobblemon.mod.common.api.spawning.context.SpawningContext
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail
import com.cobblemon.mod.common.api.spawning.spawner.Spawner
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.api.types.tera.TeraTypes
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.item.interactive.PokerodItem
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbility
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import kotlin.random.Random.Default.nextInt

/**
 * A spawning cause that is embellished with nest information.
 *
 * @author Hiroku
 * @since February 3rd, 2024
 */
class NestSpawnCause(
    spawner: Spawner,
    bucket: SpawnBucket,
    entity: Entity?
) : SpawnCause(spawner, bucket, entity) {
    // Crab Note: todo what do we put here? a reference to the nest entity causing this spawn cause?
}