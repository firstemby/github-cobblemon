/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model

import com.cobblemon.mod.common.api.cinematic.model.tracks.ActorTrack

/** Null visibility carries over between beats; tracks never carry over. */
data class ActorDirection(
    val visible: Boolean? = null,
    val startPose: StartPose? = null,
    val startPoseMode: StartPoseMode? = null,
    val tracks: List<ActorTrack> = emptyList()
) {
    init {
        if (startPose != null) {
            require(visible != false) {
                "ActorDirection.startPose declared but visible = false; hidden actors cannot have a start pose"
            }
        }
    }
}
