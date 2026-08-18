/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui

import java.util.concurrent.atomic.AtomicLong

object MolangUiDiagnostics {
    private val activeSessions = AtomicLong()
    private val rejectedActions = AtomicLong()
    private val compatibilityFailures = AtomicLong()

    internal fun sessionOpened() { activeSessions.incrementAndGet() }
    internal fun sessionClosed() { activeSessions.updateAndGet { value -> (value - 1).coerceAtLeast(0) } }
    internal fun actionRejected() { rejectedActions.incrementAndGet() }
    internal fun compatibilityFailures(count: Long) { compatibilityFailures.addAndGet(count) }

    fun snapshot() = Snapshot(activeSessions.get(), rejectedActions.get(), compatibilityFailures.get(), MolangUiContractRegistry.parseFailureCount())

    data class Snapshot(val activeSessions: Long, val rejectedActions: Long, val compatibilityFailures: Long, val parseFailures: Long)
}
