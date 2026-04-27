/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.habitats

/**
 * Controls how habitats change through all the possible phases.
 *
 * @author Hiroku
 * @since February 23rd, 2026
 */
enum class HabitatPhaseOrder {
    /** Literally in ascending order, 1-2-3-4. */
    SIMPLE,
    /** It randomly chooses an order then uses that order predictably. 3-1-4-2-3-1-4-2 for example */
    FIXED_RANDOM,
    /** Completely random phase each day. Like, 1-1-2-4-1-2-1-2-4-1-3 */
    FULL_RANDOM
}