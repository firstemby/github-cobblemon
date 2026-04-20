/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui

import com.google.gson.JsonObject
import net.minecraft.resources.ResourceLocation

/**
 * A Molang UI definition loaded from resource packs (assets/).
 *
 * Contains JSON metadata and a declarative widget tree that defines
 * the GUI layout and behavior using Molang expressions.
 *
 * @param guiId The resource location identifier for this GUI
 * @param version Semver version string for cache/manifest comparison
 * @param mode Whether this renders as an OVERLAY or SCREEN
 * @param parameters Expected parameter definitions
 * @param blocksInput Whether this GUI blocks player input
 * @param exclusive When true, suppresses all other Cobblemon overlays while this GUI is active
 * @param rootJson The raw JSON widget tree (parsed into WidgetDefinition on client)
 * @param variables Client-side Molang variables with default values
 * @param imports Template library imports
 */
data class MolangUiDefinition(
    val guiId: ResourceLocation,
    val version: String = "0.0.0",
    val mode: GuiMode = GuiMode.OVERLAY,
    val parameters: Map<String, MolangUiParameter> = emptyMap(),
    val blocksInput: Boolean = false,
    val exclusive: Boolean = false,
    val rootJson: JsonObject = JsonObject(),
    val variables: Map<String, Double> = emptyMap(),
    val imports: List<String> = emptyList()
)
