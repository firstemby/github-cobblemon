/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain

/**
 * A simple enum representing the status of a movement action.
 *
 * @author Hiroku
 * @since December 29th, 2025
 */
enum class MovementStatus {
    /** Represents when the entity is still en route to the destination. */
    PENDING,
    /** Represents when the entity has successfully reached the destination. */
    COMPLETED,
    /**
     * Represents when the entity has failed to reach the destination. This can be due to
     * a change in surroundings or taking too long or all sorts of things.
     */
    FAILED
}