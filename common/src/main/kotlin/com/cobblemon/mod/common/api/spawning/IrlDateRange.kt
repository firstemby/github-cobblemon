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