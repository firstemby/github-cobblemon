/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective

/**
 * The current status of an [Objective]. Used for the debug overlay.
 *
 * @author Hiroku
 * @since December 29th, 2025
 */
enum class ObjectiveStatus {
    /** States that this objective owns the current fixation. */
    FIXATED,
    /** States that the objective's logic is ongoing and not impeded. */
    ACTIVE,
    /** States that the objective is not performing any meaningful logic right now but isn't actively blocked. */
    INACTIVE,
    /** States that there are other objectives, fixations, etc. in play which prevents the objective from being performed. */
    BLOCKED,
    /** States that the objective is not valid and shouldn't have been registered on the entity in the first place, such as when placed on the wrong entity type. */
    INVALID
}