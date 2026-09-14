/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic

import imgui.ImGui
import imgui.flag.ImGuiDir
import imgui.internal.ImGui as ImInternal
import imgui.internal.flag.ImGuiDockNodeFlags as InternalDockNodeFlags
import imgui.type.ImInt

object EditorLayoutBuilder {

    /** Window names must match panel `ImGui.begin(name)` calls. */
    const val WINDOW_SCENE = "###Scene"
    const val WINDOW_INSPECTOR = "###Inspector"
    const val WINDOW_TIMELINE = "###Timeline"

    fun applyDefault(dockspaceId: Int, width: Float, height: Float) {
        ImInternal.dockBuilderRemoveNode(dockspaceId)

        ImInternal.dockBuilderAddNode(dockspaceId, InternalDockNodeFlags.DockSpace)

        ImInternal.dockBuilderSetNodeSize(dockspaceId, width, height)

        // Splitting consumes the dock node; use the returned opposite-side node for the next split.
        val afterBottom = ImInt()
        val minimumTimeline = ImGui.getTextLineHeightWithSpacing() * 7f + ImGui.getFrameHeight() * 2f + 60f
        val bottomRatio = (minimumTimeline / height.coerceAtLeast(1f)).coerceIn(0.36f, 0.55f)
        val bottomId = ImInternal.dockBuilderSplitNode(dockspaceId, ImGuiDir.Down, bottomRatio, null, afterBottom)

        val scale = ImGui.getIO().fontGlobalScale
        val compact = width < 1100f * scale
        val afterRight = ImInt()
        val rightRatio = (340f * scale / width).coerceIn(0.22f, 0.48f)
        val rightId = ImInternal.dockBuilderSplitNode(afterBottom.get(), ImGuiDir.Right, rightRatio, null, afterRight)
        val leftId = if (compact) rightId else ImInternal.dockBuilderSplitNode(
            afterRight.get(), ImGuiDir.Left, (250f * scale / (width * (1f - rightRatio))).coerceIn(0.18f, 0.35f), null, null
        )
        ImInternal.dockBuilderDockWindow(WINDOW_INSPECTOR, rightId)
        ImInternal.dockBuilderDockWindow(WINDOW_SCENE, leftId)
        ImInternal.dockBuilderDockWindow(WINDOW_TIMELINE, bottomId)

        ImInternal.dockBuilderFinish(dockspaceId)
    }
}
