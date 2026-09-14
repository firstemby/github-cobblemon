/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic

import com.cobblemon.mod.common.client.keybind.keybinds.CameraEditorBinding
import net.minecraft.client.resources.language.I18n
import com.cobblemon.mod.common.client.cinematic.ClientSceneSession
import com.cobblemon.mod.common.client.cinematic.ClientSceneSessionManager
import com.cobblemon.mod.common.client.editor.cinematic.overlay.ActorOverlays
import com.cobblemon.mod.common.client.editor.cinematic.overlay.ShotOverlays
import com.cobblemon.mod.common.client.editor.cinematic.overlay.TakeoverHUD
import com.cobblemon.mod.common.client.editor.cinematic.panel.SceneInspectorPanel
import com.cobblemon.mod.common.client.editor.cinematic.panel.ScenePanel
import com.cobblemon.mod.common.client.editor.cinematic.panel.TakeoverWatchdogPanel
import com.cobblemon.mod.common.client.editor.cinematic.panel.TimelinePanel
import com.cobblemon.mod.common.client.editor.cinematic.viewport.EditorCameraProjection
import com.cobblemon.mod.common.client.editor.cinematic.viewport.EditorViewportState
import com.cobblemon.mod.common.client.editor.cinematic.viewport.ViewportCameraMode
import com.cobblemon.mod.common.client.editor.cinematic.viewport.ViewportGizmoInteraction
import com.cobblemon.mod.common.client.editor.cinematic.viewport.ViewportHud
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.SceneSerializer
import com.cobblemon.mod.common.client.editor.model.cinematic.SplinePointTarget
import com.cobblemon.mod.common.client.imgui.CobblemonImGui
import com.cobblemon.mod.common.client.render.cinematic.CinematicWorldRenderer
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiDockNodeFlags
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import imgui.internal.ImGui as ImGuiInternal
import com.cobblemon.mod.common.platform.events.PlatformEvents
import java.nio.file.Path
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3

object SceneEditorOverlay {
    var activeScene: EditorScene? = null
        private set

    val selection = SceneSelectionManager()
    val playState = EditorPlayState()
    val viewState = ViewportOverlayState()
    val preview = EditorScenePreview(playState, ::activeScene)
    private val playerMovement = EditorPlayerMovement()

    @Suppress("unused")
    private val ensureShotOverlaysLoaded = ShotOverlays

    @Suppress("unused")
    private val ensureActorOverlaysLoaded = ActorOverlays

    @Suppress("unused")
    private val ensureCameraProjectionLoaded = EditorCameraProjection

    @Suppress("unused")
    private val ensureWorldRendererLoaded = CinematicWorldRenderer

    val currentPlayheadSeconds: Float get() = timelinePanel.playhead.time

    val selectedKeyframeRow: Int get() = when (val target = ViewportGizmoInteraction.selected) {
        is SplinePointTarget ->
            if (target.shotId == selection.getSelectedShotId()) target.index else -1
        else -> inspectorPanel.currentKeyframeRow
    }


    init {
        PlatformEvents.CLIENT_TICK_POST.subscribe { preview.tickTransport() }
        playState.onModeChanged { mode ->
            if (mode == EditorPlayMode.TAKEOVER) {
                TakeoverWatchdog.onEnterTakeover(playState, activeScene)
            }
        }
    }

    private val scenePanel = ScenePanel()
    private val inspectorPanel = SceneInspectorPanel()
    private val timelinePanel = TimelinePanel()
    private val takeoverWatchdogPanel = TakeoverWatchdogPanel()

    private var layoutBuilt = false
    private var layoutWidth = 0f
    private var layoutHeight = 0f
    private var requestedWidth = 0f
    private var requestedHeight = 0f
    private var resizeTime = 0.0
    var previewOrigin: Vec3 = Vec3.ZERO
        private set

    fun newScene() {
        openScene(EditorScene.createDefault())
    }

    fun seek(time: Float) {
        if (!time.isFinite()) return
        timelinePanel.playhead.setTime(time.coerceAtLeast(0f))
        preview.seek()
    }

    fun applyPreviewTime(time: Float) { timelinePanel.playhead.setTime(time) }

    fun freePreviewSession(): ClientSceneSession? {
        if (!CobblemonImGui.isActive() || playState.viewportCameraMode !=
            ViewportCameraMode.FREE_FLY) return null
        return preview.runningSceneInstanceIdOrNull()?.let {
            ClientSceneSessionManager.getSession(it)
        }
    }

    fun openScene(scene: EditorScene, origin: Vec3? = null) {
        EditorRoutePreview.clear()
        playState.setMode(EditorPlayMode.EDIT)
        playState.resetTakeoverSafety()
        EditorPathCache.clear()
        activeScene = scene
        previewOrigin = origin ?: Minecraft.getInstance().player?.position() ?: Vec3.ZERO
        selection.clear()
        selection.select(SceneSelection.BeatSelected(scene.startBeatId))
        timelinePanel.playhead.setTime(0f)
        ViewportGizmoInteraction.reset()
    }

    fun openSceneFromFile(path: Path): Boolean {
        val scene = SceneSerializer.loadFromFile(path) ?: return false
        openScene(scene)
        return true
    }

    fun closeScene() {
        EditorRoutePreview.clear()
        playState.setMode(EditorPlayMode.EDIT)
        EditorPathCache.clear()
        activeScene = null
        selection.clear()
        ViewportGizmoInteraction.reset()
    }

    fun restorePlayerMovement() = playerMovement.restore()

    fun onEditorClosed() {
        ViewportGizmoInteraction.reset()
    }

    fun render() {
        EditorDiagnostics.beginFrame()
        TakeoverWatchdog.guardTick(playState) {
            preview.tick()
        }

        val scene = activeScene

        if (scene != null) {
            handleShortcuts()
        }

        // Free-fly also works before a scene is loaded.
        playerMovement.update(Minecraft.getInstance().player,
            CobblemonImGui.isActive() && playState.viewportCameraMode == ViewportCameraMode.FREE_FLY && playState.noClip)

        renderMenuBar(scene)
        EditorSceneFiles.render()

        if (scene == null) {
            EditorViewportState.deactivate()
            renderWelcomeScreen()
            TakeoverHUD.render(playState)
            takeoverWatchdogPanel.render()
            return
        }

        val toolbarHeight = ImGui.getFrameHeightWithSpacing() * 2f + 12f
        val statusHeight = (ImGui.getTextLineHeight() + ImGui.getStyle().windowPaddingY * 2f).coerceAtLeast(32f)
        renderTransport(scene, toolbarHeight)
        setupDockspace(toolbarHeight, statusHeight)

        scenePanel.render(scene, selection)
        inspectorPanel.render(scene, selection, timelinePanel.playhead.time)
        timelinePanel.followPlayback(playState.mode != EditorPlayMode.EDIT && !preview.paused)
        timelinePanel.render(scene, selection)

        ViewportHud.render(playState, selection.getSelectedBeatId() ?: scene.startBeatId, timelinePanel.playhead.time)

        ViewportGizmoInteraction.update(scene, selection)

        run {
            val vp = ImGui.getMainViewport()
            ImGui.setNextWindowPos(vp.posX, vp.posY + vp.sizeY - statusHeight)
            ImGui.setNextWindowSize(vp.sizeX, statusHeight)
            ImGui.begin("##editorStatusBar", ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.NoResize or
                ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoNavFocus or ImGuiWindowFlags.NoDocking or ImGuiWindowFlags.NoScrollbar)
            EditorDiagnostics.panel("Status")
            ImGui.text(I18n.get("cobblemon.cinematic_editor.status", if (scene.isDirty) I18n.get("cobblemon.cinematic_editor.status.unsaved", selection.describeSelection()) else selection.describeSelection(), EditorViewportState.frameWidth, EditorViewportState.frameHeight))
            ImGui.end()
        }

        TakeoverHUD.render(playState)
        takeoverWatchdogPanel.render()
    }

    private fun handleShortcuts() {
        val io = ImGui.getIO()
        if (io.getWantTextInput()) return
        val ctrl = ImGui.isKeyDown(ImGuiKey.ModCtrl)

        if (ctrl && ImGui.isKeyPressed(ImGuiKey.S)) EditorSceneFiles.save()
        val dragging = ViewportGizmoInteraction.isDragging
        if (ctrl && !dragging && ImGui.isKeyPressed(ImGuiKey.Z)) activeScene?.undo()
        if (ctrl && !dragging && ImGui.isKeyPressed(ImGuiKey.Y)) activeScene?.redo()
        if (ImGui.isKeyPressed(ImGuiKey.Space)) preview.togglePlayback()
        if (ImGui.isKeyPressed(ImGuiKey.Escape)) playState.exitTakeover()
    }

    private fun renderMenuBar(scene: EditorScene?) {
        if (!ImGui.beginMainMenuBar()) return
        if (ImGui.beginMenu(I18n.get("cobblemon.cinematic_editor.menu.file"))) {
            if (ImGui.menuItem(I18n.get("cobblemon.cinematic_editor.file.new"))) EditorSceneFiles.requestNew()
            if (ImGui.menuItem(I18n.get("cobblemon.cinematic_editor.file.open_dialog"))) EditorSceneFiles.requestOpen()
            ImGui.separator()
            val canSave = scene != null
            if (ImGui.menuItem(I18n.get("cobblemon.cinematic_editor.file.save"), "Ctrl+S", false, canSave)) {
                EditorSceneFiles.save()
            }
            if (ImGui.menuItem(I18n.get("cobblemon.cinematic_editor.file.save_as"), "", false, canSave)) EditorSceneFiles.requestSaveAs()
            if (ImGui.menuItem(I18n.get("cobblemon.cinematic_editor.menu.apply_stadium"), "", false, EditorStadiumBridge.canApply())) EditorStadiumBridge.apply()
            ImGui.separator()
            if (ImGui.menuItem(I18n.get("cobblemon.cinematic_editor.menu.close"), CameraEditorBinding.translatedKeyMessage.string)) CobblemonImGui.toggle()
            ImGui.endMenu()
        }
        if (ImGui.beginMenu(I18n.get("cobblemon.cinematic_editor.menu.edit"))) {
            if (ImGui.menuItem(I18n.get("cobblemon.cinematic_editor.menu.undo"), "Ctrl+Z", false, scene?.canUndo() == true)) scene?.undo()
            if (ImGui.menuItem(I18n.get("cobblemon.cinematic_editor.menu.redo"), "Ctrl+Y", false, scene?.canRedo() == true)) scene?.redo()
            ImGui.endMenu()
        }
        if (ImGui.beginMenu(I18n.get("cobblemon.cinematic_editor.menu.view"))) {
            if (ImGui.menuItem(I18n.get("cobblemon.cinematic_editor.view.camera_paths"), "", viewState.cameraPathsAndFrustums)) viewState.toggleCameraPathsAndFrustums()
            if (ImGui.menuItem(I18n.get("cobblemon.cinematic_editor.view.actors"), "", viewState.actors)) viewState.toggleActors()
            if (ImGui.menuItem(I18n.get("cobblemon.cinematic_editor.view.keyframes"), "", viewState.keyframesAndGizmos)) viewState.toggleKeyframesAndGizmos()
            if (ImGui.menuItem(I18n.get("cobblemon.cinematic_editor.view.all_shots"), "", viewState.showAllShots)) viewState.toggleShowAllShots()
            if (ImGui.menuItem(I18n.get("cobblemon.cinematic_editor.view.focus_lines"), "", viewState.focusLines)) viewState.toggleFocusLines()
            if (ImGui.menuItem(I18n.get("cobblemon.cinematic_editor.view.origin"), "", viewState.originAndCompass)) viewState.toggleOriginAndCompass()
            if (ImGui.menuItem(I18n.get("cobblemon.cinematic_editor.view.grid"), "", viewState.gridOverlay)) viewState.toggleGridOverlay()
            ImGui.separator()
            if (ImGui.menuItem(I18n.get("cobblemon.cinematic_editor.view.watchdog"), null, takeoverWatchdogPanel.isVisible())) {
                takeoverWatchdogPanel.setVisible(!takeoverWatchdogPanel.isVisible())
            }
            ImGui.separator()
            if (ImGui.menuItem(I18n.get("cobblemon.cinematic_editor.view.no_clip"), "", playState.noClip)) playState.toggleNoClip()
            ImGui.endMenu()
        }
        if (ImGui.beginMenu(I18n.get("cobblemon.cinematic_editor.menu.window"))) {
            if (ImGui.menuItem(I18n.get("cobblemon.cinematic_editor.menu.reset_layout"))) resetLayout()
            if (ImGui.beginMenu(I18n.get("cobblemon.cinematic_editor.menu.ui_scale"))) {
                for (scale in listOf(1f, 1.25f, 1.5f, 2f)) {
                    if (ImGui.menuItem("${(scale * 100).toInt()}%", "", ImGui.getIO().fontGlobalScale == scale)) {
                        ImGui.getIO().fontGlobalScale = scale
                        resetLayout()
                    }
                }
                ImGui.endMenu()
            }
            if (ImGui.beginMenu(I18n.get("cobblemon.cinematic_editor.menu.inspection"))) {
                if (ImGui.menuItem(I18n.get("cobblemon.cinematic_editor.menu.control_bounds"), "", EditorDiagnostics.showBounds)) EditorDiagnostics.showBounds = !EditorDiagnostics.showBounds
                if (ImGui.menuItem(I18n.get("cobblemon.cinematic_editor.menu.capture"))) EditorDiagnostics.requestCapture()
                ImGui.endMenu()
            }
            ImGui.endMenu()
        }
        ImGui.endMainMenuBar()
    }

    private fun renderTransport(scene: EditorScene, height: Float) {
        val vp = ImGui.getMainViewport()
        ImGui.setNextWindowPos(vp.workPosX, vp.workPosY)
        ImGui.setNextWindowSize(vp.workSizeX, height)
        ImGui.begin("##transport", ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.NoResize or
            ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoDocking or ImGuiWindowFlags.NoScrollbar)
        EditorDiagnostics.panel("Transport")
        renderPlaybackControls(scene)
        ImGui.text(I18n.get("cobblemon.cinematic_editor.transport.beat"))
        ImGui.sameLine()
        ImGui.setNextItemWidth((ImGui.getContentRegionAvailX() - 120f).coerceIn(80f, 280f))
        val activeBeat = selection.getSelectedBeatId()?.takeIf { it in scene.beats } ?: scene.startBeatId
        if (ImGui.beginCombo("##activeBeat", activeBeat)) {
            for (beatId in scene.beats.keys) {
                if (ImGui.selectable(beatId, beatId == activeBeat)) {
                    selection.select(SceneSelection.BeatSelected(beatId))
                    seek(0f)
                }
            }
            ImGui.endCombo()
        }
        EditorDiagnostics.item("transport/beat")
        ImGui.sameLine()
        ImGui.text(I18n.get("cobblemon.cinematic_editor.transport.time", "%.2f".format(timelinePanel.playhead.time)))
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("cobblemon.cinematic_editor.transport.preview_help"))
        if (preview.error.isNotEmpty()) {
            EditorWidgets.nextInline(EditorWidgets.buttonWidth(I18n.get("cobblemon.cinematic_editor.transport.preview_error")))
            if (ImGui.button(I18n.get("cobblemon.cinematic_editor.transport.preview_error"))) ImGui.openPopup("Preview problem")
        }
        if (ImGui.beginPopup("Preview problem")) {
            ImGui.pushTextWrapPos(ImGui.getFontSize() * 28f)
            ImGui.textWrapped(preview.error)
            ImGui.popTextWrapPos()
            ImGui.endPopup()
        }
        ImGui.end()
    }

    private fun renderPlaybackControls(scene: EditorScene?) {
        val mode = playState.mode

        val playActive = mode != EditorPlayMode.EDIT
        if (playActive) ImGui.pushStyleColor(ImGuiCol.Button, 0.36f, 0.50f, 0.88f, 1.0f)
        if (ImGui.button(if (playActive && !preview.paused) I18n.get("cobblemon.cinematic_editor.transport.pause") else I18n.get("cobblemon.cinematic_editor.transport.play"))) if (scene != null) preview.togglePlayback()
        if (playActive) ImGui.popStyleColor()

        ImGui.sameLine()
        val editActive = mode == EditorPlayMode.EDIT
        if (editActive) ImGui.pushStyleColor(ImGuiCol.Button, 0.23f, 0.26f, 0.34f, 1.0f)
        if (ImGui.button(I18n.get("cobblemon.cinematic_editor.transport.stop"))) playState.setMode(EditorPlayMode.EDIT)
        if (editActive) ImGui.popStyleColor()

        ImGui.sameLine()
        if (ImGui.button("<")) seek(currentPlayheadSeconds - 0.05f)
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("cobblemon.cinematic_editor.transport.previous_frame"))
        ImGui.sameLine()
        if (ImGui.button(">")) seek(currentPlayheadSeconds + 0.05f)
        if (ImGui.isItemHovered()) ImGui.setTooltip(I18n.get("cobblemon.cinematic_editor.transport.next_frame"))

        ImGui.sameLine()
        val takeoverActive = mode == EditorPlayMode.TAKEOVER
        if (takeoverActive) ImGui.pushStyleColor(ImGuiCol.Button, 0.96f, 0.62f, 0.04f, 1.0f)
        if (ImGui.button(I18n.get("cobblemon.cinematic_editor.transport.takeover"))) {
            if (scene != null) playState.setMode(EditorPlayMode.TAKEOVER)
        }
        if (takeoverActive) ImGui.popStyleColor()

        ImGui.sameLine()
        val sceneCam = playState.viewportCameraMode == ViewportCameraMode.SCENE_CAM
        if (sceneCam) ImGui.pushStyleColor(ImGuiCol.Button, 0.55f, 0.36f, 0.94f, 1.0f)
        if (ImGui.button(if (sceneCam) I18n.get("cobblemon.cinematic_editor.transport.scene_camera") else I18n.get("cobblemon.cinematic_editor.transport.free_camera"))) {
            if (scene != null) playState.toggleViewportCamera()
        }
        if (sceneCam) ImGui.popStyleColor()

    }

    private fun setupDockspace(toolbarHeight: Float, statusHeight: Float) {
        val viewport = ImGui.getMainViewport()
        val height = (viewport.workSizeY - toolbarHeight - statusHeight).coerceAtLeast(1f)
        ImGui.setNextWindowPos(viewport.workPosX, viewport.workPosY + toolbarHeight)
        ImGui.setNextWindowSize(viewport.workSizeX, height)
        ImGui.setNextWindowViewport(viewport.id)
        val flags = ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.NoCollapse or
            ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoMove or
            ImGuiWindowFlags.NoBringToFrontOnFocus or ImGuiWindowFlags.NoNavFocus or
            ImGuiWindowFlags.NoBackground
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0f)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0f)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)
        ImGui.begin("##SceneEditorDockspace", flags)
        ImGui.popStyleVar(3)
        val dockspaceId = ImGui.getID("CinematicEditorDockspace")

        if (requestedWidth != viewport.workSizeX || requestedHeight != height) {
            requestedWidth = viewport.workSizeX
            requestedHeight = height
            resizeTime = ImGui.getTime()
        }
        if (ImGui.getTime() - resizeTime > 0.15 && (layoutWidth != requestedWidth || layoutHeight != requestedHeight)) layoutBuilt = false

        if (!layoutBuilt) {
            EditorLayoutBuilder.applyDefault(dockspaceId, viewport.workSizeX, height)
            layoutWidth = viewport.workSizeX
            layoutHeight = height
            layoutBuilt = true
        }

        ImGui.dockSpace(dockspaceId, 0f, 0f, ImGuiDockNodeFlags.PassthruCentralNode)

        val central = try {
            ImGuiInternal.dockBuilderGetCentralNode(dockspaceId)
        } catch (_: Throwable) { null }
        if (central != null && central.isValidPtr()) {
            val px = central.posX.toInt(); val py = central.posY.toInt()
            val pw = central.sizeX.toInt(); val ph = central.sizeY.toInt()
            // Use real ImGui display dimensions; Window dimensions are overridden to the viewport size.
            EditorViewportState.publish(px, py, pw, ph, viewport.sizeX.toInt(), viewport.sizeY.toInt())
        } else {
            EditorViewportState.deactivate()
        }

        ImGui.end()
    }

    fun resetLayout() {
        layoutBuilt = false
    }

    private fun renderWelcomeScreen() {
        ImGui.setNextWindowSize((ImGui.getIO().displaySizeX - 40f).coerceAtMost(480f), 210f, ImGuiCond.Appearing)
        ImGui.begin(I18n.get("cobblemon.cinematic_editor.welcome.title") + "###Cinematic Scene Editor")
        ImGui.textWrapped(I18n.get("cobblemon.cinematic_editor.welcome.description"))
        if (ImGui.button(I18n.get("cobblemon.cinematic_editor.file.new"))) EditorSceneFiles.requestNew()
        if (ImGui.button(I18n.get("cobblemon.cinematic_editor.file.open_dialog"))) EditorSceneFiles.requestOpen()
        ImGui.textWrapped(I18n.get("cobblemon.cinematic_editor.welcome.help", CameraEditorBinding.translatedKeyMessage.string))
        ImGui.end()
    }
}
