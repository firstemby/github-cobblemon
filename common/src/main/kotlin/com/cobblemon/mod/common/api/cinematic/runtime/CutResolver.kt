/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime

import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.CutEntry
import com.cobblemon.mod.common.api.cinematic.model.CutTransition

class CutResolver {

    /** A feed without a cut track is inactive in this beat. */
    fun resolveLiveShot(beat: Beat, feedId: String, beatTime: Float): Resolution? {
        val cutTrack = beat.cuts[feedId] ?: return null
        val entry = pickEntry(cutTrack.entries, beatTime) ?: return null
        return Resolution(shotId = entry.shotId, transition = entry.transition)
    }

    /** Cuts must be time-sorted; before the first cut, its shot is used, and equal-time cuts favor the last. */
    private fun pickEntry(entries: List<CutEntry>, beatTime: Float): CutEntry? {
        if (entries.isEmpty()) return null
        return entries.lastOrNull { it.time <= beatTime }
            ?: entries.first()
    }

    data class Resolution(
        val shotId: String,
        val transition: CutTransition
    )
}
