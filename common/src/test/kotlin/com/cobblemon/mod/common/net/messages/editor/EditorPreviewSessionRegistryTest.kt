/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.editor

import com.cobblemon.mod.common.net.messages.server.editor.EditorPreviewControlC2SPacket
import com.cobblemon.mod.common.net.serverhandling.editor.EditorPreviewSessionRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID

class EditorPreviewSessionRegistryTest {
    @AfterEach
    fun clear() { EditorPreviewSessionRegistry.clear() }

    @Test
    fun `controls require ownership reject stale revisions and coalesce to latest`() {
        val owner = UUID.randomUUID()
        val id = UUID.randomUUID()
        val session = EditorPreviewSessionRegistry.register(id, UUID.randomUUID(), owner)
        assertThrows(IllegalStateException::class.java) {
            EditorPreviewSessionRegistry.register(id, UUID.randomUUID(), UUID.randomUUID())
        }
        fun control(revision: Long, time: Float = 2f) = EditorPreviewControlC2SPacket(id, revision, "main", time, true)
        assertFalse(EditorPreviewSessionRegistry.enqueue(control(1), UUID.randomUUID()))
        assertFalse(EditorPreviewSessionRegistry.enqueue(control(1, Float.NaN), owner))
        assertTrue(EditorPreviewSessionRegistry.enqueue(control(1), owner))
        assertTrue(EditorPreviewSessionRegistry.enqueue(control(5, 4f), owner))
        assertFalse(EditorPreviewSessionRegistry.enqueue(control(3), owner))
        assertEquals(4f, session.pending?.time)
        assertNull(EditorPreviewSessionRegistry.consume(id, UUID.randomUUID()))
        assertEquals(session.sceneId, EditorPreviewSessionRegistry.consume(id, owner))
        assertFalse(EditorPreviewSessionRegistry.enqueue(control(6), owner))
    }
}
