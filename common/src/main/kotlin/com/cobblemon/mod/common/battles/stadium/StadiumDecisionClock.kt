/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.stadium

class StadiumDecisionClock(private val turnSeconds: Int, matchMinutes: Int) {
    data class Decision(val rqid: Int, var ticks: Int)
    val decisions = mutableMapOf<String, Decision>()
    var matchTicks = if (matchMinutes > 0) matchMinutes * 1200 else -1
        private set

    fun request(side: String, rqid: Int) {
        if (decisions[side]?.rqid != rqid) decisions[side] = Decision(rqid, if (turnSeconds > 0) turnSeconds * 20 else -1)
    }

    fun tick(active: Set<String>): Set<String> {
        if (active.isEmpty()) return emptySet()
        if (matchTicks > 0) matchTicks--
        active.forEach { side -> decisions[side]?.let { if (it.ticks > 0) it.ticks-- } }
        return active.filter { decisions[it]?.ticks == 0 }.toSet()
    }

    fun seconds(side: String) = decisions[side]?.ticks?.let { if (it < 0) -1 else (it + 19) / 20 } ?: -1
    fun matchSeconds() = if (matchTicks < 0) -1 else (matchTicks + 19) / 20
}
