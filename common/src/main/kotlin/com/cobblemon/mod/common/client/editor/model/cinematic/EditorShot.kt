/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.model.cinematic

import com.google.gson.JsonObject

class EditorShot(
    var id: String,
    var positionData: JsonObject,
    var rotationData: JsonObject,
    var fovData: JsonObject? = null,
    var rollData: JsonObject? = null,
    var lens: EditorLens? = null,
    val modifiers: MutableList<JsonObject> = mutableListOf()
)
