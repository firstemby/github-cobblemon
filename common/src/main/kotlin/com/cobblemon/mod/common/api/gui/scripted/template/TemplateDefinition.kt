/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.gui.scripted.template

import com.google.gson.JsonObject

data class TemplateDefinition(
    val name: String,
    val namespace: String,
    val inputs: Map<String, TemplateInput> = emptyMap(),
    val extends: String? = null,
    val body: JsonObject
)

data class TemplateInput(
    val type: String = "string",
    val default: String? = null
)
