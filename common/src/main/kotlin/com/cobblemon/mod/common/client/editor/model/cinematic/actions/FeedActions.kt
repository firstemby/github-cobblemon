/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.model.cinematic.actions

import com.cobblemon.mod.common.client.editor.model.cinematic.EditorFeed
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.SceneEditorAction

class AddFeed(private val feed: EditorFeed) : SceneEditorAction {
    override fun execute(scene: EditorScene) { scene.feeds.add(feed) }
    override fun undo(scene: EditorScene) {
        val idx = scene.feeds.indexOf(feed)
        if (idx >= 0) scene.feeds.removeAt(idx)
    }
}

class RemoveFeed(private val index: Int, private val feed: EditorFeed) : SceneEditorAction {
    override fun execute(scene: EditorScene) {
        if (index >= 0 && index < scene.feeds.size) scene.feeds.removeAt(index)
    }
    override fun undo(scene: EditorScene) {
        scene.feeds.add(index.coerceIn(0, scene.feeds.size), feed)
    }
}
