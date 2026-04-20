/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui

/**
 * Defines a parameter expected by a scripted GUI.
 *
 * @param type The parameter type: "number", "string", or "vec3"
 * @param default The default value as a string, or null if required
 */
data class MolangUiParameter(
    val type: String = "string",
    val default: String? = null
)
