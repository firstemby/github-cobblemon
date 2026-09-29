/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.camera.result

import net.minecraft.network.chat.Component

data class ValidationError(
    val path: String,
    val message: Component,
    val severity: Severity = Severity.ERROR
) {
    enum class Severity { WARNING, ERROR }

    override fun toString(): String = "[$severity] $path: ${message.string}"
}
