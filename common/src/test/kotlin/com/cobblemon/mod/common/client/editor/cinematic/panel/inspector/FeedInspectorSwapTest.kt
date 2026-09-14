/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import com.cobblemon.mod.common.api.cinematic.model.FeedKind
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorFeed
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FeedInspectorSwapTest {

    @Test
    fun `confirmation cannot replace a different or removed feed`() {
        val scene = EditorScene.createDefault()
        val first = EditorFeed("primary", FeedKind.Pip(FeedKind.Corner.TOP_RIGHT, 0.4f))
        val second = EditorFeed("secondary", first.kind)
        scene.feeds.addAll(listOf(first, second))
        val inspector = FeedInspector()

        inspector.requestSwap(scene, first, FeedKindChoice.SPLIT)
        assertFalse(inspector.confirmSwap(scene, second))
        inspector.requestSwap(scene, first, FeedKindChoice.SPLIT)
        assertFalse(inspector.confirmSwap(EditorScene.createDefault(), first))
        inspector.requestSwap(scene, first, FeedKindChoice.SPLIT)
        scene.feeds.remove(first)
        assertFalse(inspector.confirmSwap(scene, first))
        assertEquals(second.kind, first.kind)
        assertFalse(scene.canUndo())
    }

    @Test
    fun `confirmation rejects changed settings and valid swap supports undo`() {
        val scene = EditorScene.createDefault()
        val feed = EditorFeed("primary", FeedKind.Pip(FeedKind.Corner.TOP_RIGHT, 0.4f))
        scene.feeds.add(feed)
        val inspector = FeedInspector()
        inspector.requestSwap(scene, feed, FeedKindChoice.SPLIT)
        val edited = FeedKind.Pip(FeedKind.Corner.BOTTOM_LEFT, 0.6f)
        feed.kind = edited
        assertFalse(inspector.confirmSwap(scene, feed))
        assertEquals(edited, feed.kind)
        assertFalse(scene.canUndo())

        inspector.requestSwap(scene, feed, FeedKindChoice.SPLIT)
        assertTrue(inspector.confirmSwap(scene, feed))
        assertEquals(FeedInspector.defaultKind(FeedKindChoice.SPLIT), feed.kind)
        assertTrue(scene.undo())
        assertEquals(edited, feed.kind)
        assertFalse(scene.canUndo())
        assertFalse(inspector.confirmSwap(scene, feed))
    }

    @Test
    fun `defaultKind produces valid engine FeedKind for each enum`() {
        assertTrue(FeedInspector.defaultKind(FeedKindChoice.FULLSCREEN) is FeedKind.Fullscreen)
        assertTrue(FeedInspector.defaultKind(FeedKindChoice.PIP) is FeedKind.Pip)
        assertTrue(FeedInspector.defaultKind(FeedKindChoice.SPLIT) is FeedKind.Split)
        assertTrue(FeedInspector.defaultKind(FeedKindChoice.DIAGONAL) is FeedKind.Diagonal)
    }

    @Test
    fun `choiceOf round-trips through defaultKind`() {
        for (choice in FeedKindChoice.entries) {
            val k = FeedInspector.defaultKind(choice)
            assertEquals(choice, FeedInspector.choiceOf(k))
        }
    }

    @Test
    fun `hasUserData detects default vs non-default configurations`() {
        assertFalse(FeedInspector.hasUserData(FeedKind.Fullscreen))
        assertFalse(FeedInspector.hasUserData(FeedInspector.defaultKind(FeedKindChoice.PIP)))

        val customPip = FeedKind.Pip(corner = FeedKind.Corner.TOP_RIGHT, size = 0.4f)
        assertTrue(FeedInspector.hasUserData(customPip))
    }
}
