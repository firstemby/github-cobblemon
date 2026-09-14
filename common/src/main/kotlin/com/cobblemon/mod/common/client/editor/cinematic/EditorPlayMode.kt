/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic

import com.cobblemon.mod.common.client.editor.cinematic.viewport.ViewportCameraMode

/** PLAY previews one beat; TAKEOVER currently guards navigation without binding real actors. */
enum class EditorPlayMode { EDIT, PLAY, TAKEOVER }

class EditorPlayState(initial: EditorPlayMode = EditorPlayMode.EDIT) {
    var mode: EditorPlayMode = initial
        private set

    private val listeners = mutableListOf<(EditorPlayMode) -> Unit>()

    var sessionSafeModeApplied: Boolean = false
        private set

    fun markSafeModeApplied() { sessionSafeModeApplied = true }

    fun resetTakeoverSafety() { sessionSafeModeApplied = false }

    /** Playback mode changes never switch the viewport camera automatically. */
    var viewportCameraMode: ViewportCameraMode = ViewportCameraMode.FREE_FLY
        private set

    /** Client-only no-clip does not bypass server collision in multiplayer. */
    var noClip: Boolean = false
        private set

    fun toggleNoClip() { noClip = !noClip }

    /** Callers must ensure an active scene exists. */
    fun toggleViewportCamera() {
        viewportCameraMode = if (viewportCameraMode == ViewportCameraMode.FREE_FLY)
            ViewportCameraMode.SCENE_CAM
        else
            ViewportCameraMode.FREE_FLY
    }

    fun setMode(newMode: EditorPlayMode) {
        if (mode != newMode) {
            mode = newMode
            listeners.forEach { it(newMode) }
        }
    }

    fun togglePlay() = setMode(if (mode == EditorPlayMode.EDIT) EditorPlayMode.PLAY else EditorPlayMode.EDIT)

    fun exitTakeover(): Boolean {
        if (mode != EditorPlayMode.TAKEOVER) return false
        setMode(EditorPlayMode.EDIT)
        return true
    }

    fun onModeChanged(listener: (EditorPlayMode) -> Unit) { listeners.add(listener) }
}
