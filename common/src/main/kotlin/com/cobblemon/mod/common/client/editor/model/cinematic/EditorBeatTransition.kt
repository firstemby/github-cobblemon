/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.model.cinematic

import com.cobblemon.mod.common.api.cinematic.model.CutTransition

class EditorBeatTransition(
    var sourceBeatId: String,
    var targetBeatId: String,
    var condition: String = "1.0",
    var transition: CutTransition = CutTransition.Blend(0.4f),
    var endsScene: Boolean = false
)
