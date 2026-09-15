/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui

import com.cobblemon.mod.common.api.molangui.widget.WidgetDefinition
import net.minecraft.resources.ResourceLocation

data class MolangUiDefinition(
    val guiId: ResourceLocation,
    val formatVersion: Int,
    val contractVersion: String,
    val mode: GuiMode,
    val parameters: Map<String, ParameterType>,
    val actions: Set<ActionKey>,
    val exclusive: Boolean,
    val root: WidgetDefinition,
    val variables: Map<String, Double>
)
