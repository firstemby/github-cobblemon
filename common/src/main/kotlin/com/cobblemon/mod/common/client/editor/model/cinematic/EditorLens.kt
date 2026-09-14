/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.model.cinematic

/** Editing permits out-of-range values; save and preview enforce runtime bounds. */
class EditorLens(
    var aspectCrop: Float? = null,
    var letterbox: Float = 0f
)
