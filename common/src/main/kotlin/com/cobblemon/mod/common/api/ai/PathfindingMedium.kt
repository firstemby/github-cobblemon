/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai

/**
 * The medium that an entity might be in which motivates what type of wandering it should do.
 *
 * If you've discovered this in main and it's not being used outside of that unused extension function, delete me
 *
 * @author Hiroku
 * @since January 25th, 2025
 */
enum class PathfindingMedium {
    LAND,
    WATER,
    LAVA,
    AIR
}