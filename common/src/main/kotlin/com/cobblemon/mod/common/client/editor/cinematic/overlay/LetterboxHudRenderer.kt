/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.overlay

import com.cobblemon.mod.common.api.cinematic.model.sources.Lens
import com.cobblemon.mod.common.client.editor.cinematic.EditorPlayMode
import com.cobblemon.mod.common.client.editor.cinematic.SceneEditorOverlay
import com.cobblemon.mod.common.client.editor.cinematic.SceneSelection
import com.cobblemon.mod.common.client.imgui.CobblemonImGui
import com.cobblemon.mod.common.client.render.camera.FeedLensMask
import com.cobblemon.mod.common.client.render.camera.compositor.CompositorManager
import net.minecraft.client.gui.GuiGraphics

object LetterboxHudRenderer {

    fun render(graphics: GuiGraphics) {
        if (!CobblemonImGui.isActive()) return
        if (CompositorManager.isFullscreenActive()) return
        val scene = SceneEditorOverlay.activeScene ?: return
        if (SceneEditorOverlay.playState.mode != EditorPlayMode.EDIT) return
        val selection = SceneEditorOverlay.selection.selection as? SceneSelection.ShotSelected ?: return
        val editorLens = scene.shots[selection.shotId]?.lens ?: return
        val lens = Lens(editorLens.aspectCrop?.takeIf { it.isFinite() && it > 0f },
            editorLens.letterbox.takeIf { it.isFinite() }?.coerceIn(0f, 0.5f) ?: 0f)
        for (mask in FeedLensMask.rectangles(graphics.guiWidth(), graphics.guiHeight(), lens)) {
            graphics.fill(mask.x, mask.y, mask.x + mask.width, mask.y + mask.height, 0xFF000000.toInt())
        }
    }
}
