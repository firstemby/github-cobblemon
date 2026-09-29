/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles

import com.cobblemon.mod.common.battles.runner.ShowdownService
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ShowdownThreadTest {
    @Test
    fun `launch propagates connection failure without running queued actions`() {
        val service = mockk<ShowdownService>()
        val failure = IllegalStateException("connection failed")
        mockkObject(ShowdownService.Companion)
        try {
            every { ShowdownService.service } returns service
            every { service.openConnection() } throws failure
            var actionCalled = false
            val action: (ShowdownService) -> Unit = { actionCalled = true }
            val thread = ShowdownThread()
            thread.queue(action)

            val thrown = assertThrows(IllegalStateException::class.java) { thread.launch() }

            assertSame(failure, thrown.cause)
            assertSame(failure, assertThrows(IllegalStateException::class.java) { thread.queue(action) }.cause)
            assertFalse(actionCalled)
        } finally {
            unmockkObject(ShowdownService.Companion)
        }
    }
}
