/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic

class ViewportOverlayState {
    var cameraPathsAndFrustums: Boolean = true
        private set
    var actors: Boolean = true
        private set
    var keyframesAndGizmos: Boolean = true
        private set
    var showAllShots: Boolean = false
        private set
    var focusLines: Boolean = true
        private set
    var originAndCompass: Boolean = true
        private set
    var gridOverlay: Boolean = false
        private set

    fun toggleCameraPathsAndFrustums() { cameraPathsAndFrustums = !cameraPathsAndFrustums }
    fun toggleActors() { actors = !actors }
    fun toggleKeyframesAndGizmos() { keyframesAndGizmos = !keyframesAndGizmos }
    fun toggleShowAllShots() { showAllShots = !showAllShots }
    fun toggleFocusLines() { focusLines = !focusLines }
    fun toggleOriginAndCompass() { originAndCompass = !originAndCompass }
    fun toggleGridOverlay() { gridOverlay = !gridOverlay }
}
