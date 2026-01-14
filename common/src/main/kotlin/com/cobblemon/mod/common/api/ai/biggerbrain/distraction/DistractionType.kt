/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.distraction

import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation

/**
 * A categorisation of distractions that can be attempted on an [EntityFixation].
 *
 * @author Hiroku
 * @since December 29th, 2025
 */
enum class DistractionType {
    /** Represents a distraction that the entity is not particularly motivated by but would consider doing out of boredom. */
    IDLE_BOREDOM,
    /** Represents a distraction that is for the sake of the entity's comfort because it isn't comfortable where it is. */
    COMFORT,
    /** Represents a distraction motivated by being hurt. */
    DAMAGE,
    /** A forceful distraction. Just about everything should respect this but it's conceivable that something not. */
    FORCE,
    /** Represents a distraction that aims to make the entity move. */
    MOVE,
    /** Represents a distraction that aims to make the entity look at something. */
    LOOK
}