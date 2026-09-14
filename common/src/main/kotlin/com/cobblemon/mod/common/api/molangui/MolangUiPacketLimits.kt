/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui

object MolangUiPacketLimits {
    const val MAX_CONTRACTS = 256
    const val MAX_ACTION_FIELDS = 32
    const val MAX_IDENTIFIER_LENGTH = 128
    const val MAX_VALUE_LENGTH = 4 * 1024
    const val MAX_PACKET_BYTES = 256 * 1024

    fun requireIdentifier(value: String, label: String): String {
        require(value.isNotBlank() && value.length <= MAX_IDENTIFIER_LENGTH) {
            "$label must contain 1..$MAX_IDENTIFIER_LENGTH characters"
        }
        return value
    }

    fun requireValue(value: String, label: String): String {
        require(value.length <= MAX_VALUE_LENGTH) { "$label exceeds $MAX_VALUE_LENGTH characters" }
        return value
    }
}
