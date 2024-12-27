/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning

import java.time.LocalDate

open class IrlDateRange (
    val startDate: LocalDate,
    val stopDate: LocalDate? = null
) {
    operator fun contains(date: LocalDate): Boolean {
        return if(stopDate != null) {
            (startDate.isEqual(date) || startDate.isBefore(date)) && (stopDate.isAfter(date) || stopDate.isEqual(date))
        } else {
            startDate.isEqual(date)
        }
    }
}