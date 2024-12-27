/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning

import java.time.LocalTime

open class IrlTimeRange(
    val startTime: LocalTime,
    val stopTime: LocalTime
) {
    operator fun contains(time: LocalTime): Boolean {
        return (time.isAfter(startTime) || time == startTime) && time.isBefore(stopTime)
    }
}