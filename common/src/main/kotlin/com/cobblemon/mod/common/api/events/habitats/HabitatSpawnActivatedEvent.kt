/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.events.habitats

import com.cobblemon.mod.common.api.events.Cancelable
import com.cobblemon.mod.common.api.habitats.spawningstyle.ActivatedHabitatSpawning
import com.cobblemon.mod.common.api.spawning.SpawnCause
import com.cobblemon.mod.common.api.spawning.spawner.FixedAreaSpawner
import com.cobblemon.mod.common.block.habitat.HabitatBlockEntity

/**
 * Event that fires when a habitat is about to attempt to spawn Pokémon. Cancelling this event will prevent
 * the habitat block from attempting to spawn anything for this activation.
 *
 * @author Hiroku
 * @since February 15th,2026
 */
class HabitatSpawnActivatedEvent(
    /** The habitat block entity that is responsible for the spawn. */
    val habitatBlockEntity: HabitatBlockEntity,
    /** The activation settings. You could have got it from the block entity but this is easier. */
    val activatedHabitatSpawning: ActivatedHabitatSpawning,
    /** The [SpawnCause] that will be used in the spawn action. This may contain a player that is nearest to the block. */
    var cause: SpawnCause,
    /** The maximum number of Pokémon the block will consider spawning (provided there is space). */
    var maxSpawns: Int
) : Cancelable() {
    /** The spawner representing the habitat. */
    val spawner: FixedAreaSpawner
        get() = activatedHabitatSpawning.spawner
}