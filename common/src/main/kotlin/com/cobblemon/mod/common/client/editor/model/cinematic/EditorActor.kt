/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.model.cinematic

import com.cobblemon.mod.common.api.cinematic.model.ActorKind
import net.minecraft.world.phys.Vec3

/** Preview metadata is editor-only; actual actor bindings are supplied by the runtime caller. */
class EditorActor(
    var id: String,
    var kind: ActorKind,
    var previewSpecies: String = "",
    val previewAspects: MutableSet<String> = mutableSetOf(),
    var previewCategory: String = "",
    var previewScale: Float = 1f,
    var previewOffset: Vec3 = Vec3.ZERO
)
