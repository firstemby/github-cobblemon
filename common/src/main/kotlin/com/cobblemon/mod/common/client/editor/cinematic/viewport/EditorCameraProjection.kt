/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.viewport

import com.cobblemon.mod.common.client.editor.cinematic.SceneEditorOverlay
import com.cobblemon.mod.common.client.render.camera.CameraFeedHandler
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.platform.events.RenderEvent
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector4f

// Minecraft's model-view is camera-relative; subtract the camera position before projection.
object EditorCameraProjection {

    private var viewProj: Matrix4f? = null
    private var cameraPos: Vec3 = Vec3.ZERO

    val isReady: Boolean get() = viewProj != null && EditorViewportState.active

    init {
        PlatformEvents.RENDER.subscribe { ev ->
            if (ev.stage != RenderEvent.Stage.TRANSLUCENT) return@subscribe
            if (CameraFeedHandler.isCapturing() &&
                SceneEditorOverlay.freePreviewSession() != null) return@subscribe
            viewProj = Matrix4f(ev.projectionMatrix).mul(ev.modelViewMatrix)
            cameraPos = ev.camera.position
        }
    }

    fun worldToScreen(x: Double, y: Double, z: Double): Vector2f? {
        val m = viewProj ?: return null
        if (!EditorViewportState.active) return null
        return project(
            m, cameraPos, x, y, z,
            EditorViewportState.frameX.toFloat(), EditorViewportState.frameY.toFloat(),
            EditorViewportState.frameWidth.toFloat(), EditorViewportState.frameHeight.toFloat()
        )
    }

    fun project(
        viewProj: Matrix4f,
        camera: Vec3,
        x: Double, y: Double, z: Double,
        rectX: Float, rectY: Float, rectW: Float, rectH: Float
    ): Vector2f? {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite() ||
            !rectX.isFinite() || !rectY.isFinite() || !rectW.isFinite() || !rectH.isFinite() ||
            rectW <= 0f || rectH <= 0f) return null
        val v = Vector4f(
            (x - camera.x).toFloat(),
            (y - camera.y).toFloat(),
            (z - camera.z).toFloat(),
            1f
        )
        viewProj.transform(v)
        if (!v.x.isFinite() || !v.y.isFinite() || !v.w.isFinite() || v.w <= 1e-4f) return null
        val ndcX = v.x / v.w
        val ndcY = v.y / v.w
        if (!ndcX.isFinite() || !ndcY.isFinite()) return null
        val screenX = rectX + (ndcX * 0.5f + 0.5f) * rectW
        val screenY = rectY + (1f - (ndcY * 0.5f + 0.5f)) * rectH
        if (!screenX.isFinite() || !screenY.isFinite()) return null
        return Vector2f(
            screenX, screenY
        )
    }
}
