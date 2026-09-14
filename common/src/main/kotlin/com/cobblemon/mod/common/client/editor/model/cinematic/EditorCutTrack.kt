/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.model.cinematic

import com.cobblemon.mod.common.api.cinematic.model.CutTransition

/** Entries are not auto-sorted; sort them before saving to satisfy runtime validation. */
class EditorCutTrack(
    val entries: MutableList<EditorCutEntry> = mutableListOf()
)

class EditorCutEntry(
    var time: Float,
    var shotId: String,
    var transition: CutTransition = CutTransition.Instant
)
