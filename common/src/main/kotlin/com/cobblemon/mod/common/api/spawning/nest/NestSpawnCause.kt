/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.nest

import com.cobblemon.mod.common.api.spawning.SpawnCause
import com.cobblemon.mod.common.api.spawning.spawner.Spawner
import net.minecraft.world.entity.Entity

/**
 * A spawning cause that is embellished with nest information.
 *
 * @author Hiroku
 * @since February 3rd, 2024
 */
class NestSpawnCause(
    spawner: Spawner,
    entity: Entity?
) : SpawnCause(spawner, entity) {
    // Crab Note: todo what do we put here? a reference to the nest entity causing this spawn cause?
}