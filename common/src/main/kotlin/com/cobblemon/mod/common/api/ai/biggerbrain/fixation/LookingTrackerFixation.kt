/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.fixation

import com.cobblemon.mod.common.api.ai.biggerbrain.LookingTracker

/**
 * Applied to fixations which may have looking associated with them. This is useful
 * in the case that an objective needs to know if the current fixation intends on (or is)
 * controlling their looking direction.
 *
 * Example: An objective that makes Pokémon bob their heads up and down while music is playing.
 * Said objective would not warrant a full fixation, but would still like to ensure that the
 * current fixation does not intend on using a looking tracker.
 *
 * @author Hiroku
 * @since December 29th, 2025
 */
interface LookingTrackerFixation {
    val lookingTracker: LookingTracker?
}