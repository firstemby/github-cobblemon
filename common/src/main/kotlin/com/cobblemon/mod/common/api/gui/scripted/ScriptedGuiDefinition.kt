/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.gui.scripted

import net.minecraft.resources.ResourceLocation

/**
 * A scripted GUI definition loaded from data packs.
 *
 * Consists of JSON metadata (mode, parameters, input blocking) paired with
 * a JavaScript script that handles rendering and interaction.
 *
 * @param guiId The resource location identifier for this GUI
 * @param mode Whether this renders as an OVERLAY or SCREEN
 * @param parameters Expected parameter definitions
 * @param blocksInput Whether this GUI blocks player input
 * @param exclusive When true, suppresses all other Cobblemon overlays while this GUI is active
 * @param scriptSource The JavaScript script source code
 */
data class ScriptedGuiDefinition(
    val guiId: ResourceLocation,
    val mode: GuiMode = GuiMode.OVERLAY,
    val parameters: Map<String, ScriptedGuiParameter> = emptyMap(),
    val blocksInput: Boolean = false,
    val exclusive: Boolean = false,
    val scriptSource: String = ""
)
