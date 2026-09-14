/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic

import net.minecraft.client.resources.language.I18n
import com.cobblemon.mod.common.client.editor.model.cinematic.SceneSerializer
import com.cobblemon.mod.common.Cobblemon
import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImString
import net.minecraft.client.Minecraft
import java.nio.file.Files
import java.nio.file.Path

object EditorSceneFiles {
    private class SceneFileException(message: String) : IllegalArgumentException(message)

    private val path = ImString(2048)
    private var save = false
    private var requested = false
    private var error = ""
    private var confirm = false
    private var pendingNew = false
    private val directory get() = Minecraft.getInstance().gameDirectory.toPath().resolve("cobblemon/cinematics")

    fun requestOpen() = request(false)
    fun requestSaveAs() = request(true)
    fun requestNew() {
        if (SceneEditorOverlay.activeScene?.isDirty == true) pendingNew = true else SceneEditorOverlay.newScene()
    }

    private fun request(saving: Boolean) {
        save = saving
        val filename = (SceneEditorOverlay.activeScene?.id ?: "scene").replace(Regex("[^A-Za-z0-9._-]"), "_")
        path.set(if (saving) (SceneEditorOverlay.activeScene?.sourcePath ?: directory.resolve("$filename.json")).toAbsolutePath().toString() else directory.toAbsolutePath().toString())
        error = ""
        confirm = false
        requested = true
    }

    fun save() {
        val scene = SceneEditorOverlay.activeScene ?: return
        val target = scene.sourcePath ?: return requestSaveAs()
        try {
            SceneSerializer.saveToFile(scene, target)
        } catch (exception: Exception) {
            requestSaveAs()
            Cobblemon.LOGGER.warn("Could not save cinematic scene to {}", target, exception)
            error = I18n.get("cobblemon.cinematic_editor.file.save_failed")
        }
    }

    fun render() {
        if (pendingNew) { ImGui.openPopup(I18n.get("cobblemon.cinematic_editor.file.discard_title") + "###Discard unsaved scene?"); pendingNew = false }
        if (ImGui.beginPopupModal(I18n.get("cobblemon.cinematic_editor.file.discard_title") + "###Discard unsaved scene?", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text(I18n.get("cobblemon.cinematic_editor.file.unsaved"))
            if (ImGui.button(I18n.get("cobblemon.cinematic_editor.file.discard"))) { SceneEditorOverlay.newScene(); ImGui.closeCurrentPopup() }
            ImGui.sameLine()
            if (ImGui.button(I18n.get("cobblemon.cinematic_editor.file.cancel"))) ImGui.closeCurrentPopup()
            ImGui.endPopup()
        }
        if (requested) { ImGui.openPopup(I18n.get("cobblemon.cinematic_editor.file.title") + "###Scene file"); requested = false }
        ImGui.setNextWindowSize((ImGui.getIO().displaySizeX - 40f).coerceAtMost(640f),
            (ImGui.getIO().displaySizeY - 40f).coerceAtMost(360f), ImGuiCond.Appearing)
        if (!ImGui.beginPopupModal(I18n.get("cobblemon.cinematic_editor.file.title") + "###Scene file")) return
        ImGui.text(if (save) I18n.get("cobblemon.cinematic_editor.file.save_json") else I18n.get("cobblemon.cinematic_editor.file.open_json"))
        EditorWidgets.fullWidth()
        if (ImGui.inputText("##filePath", path)) { confirm = false; error = "" }
        if (!save) {
            try {
                val folder = Path.of(path.get()).let { if (Files.isDirectory(it)) it else it.parent }
                if (folder != null && Files.isDirectory(folder)) {
                    val files = Files.list(folder).use { entries ->
                        entries.filter { Files.isRegularFile(it) && it.toString().endsWith(".json") }.sorted().limit(100).toList()
                    }
                    ImGui.beginChild("##sceneFiles", 0f, ImGui.getTextLineHeightWithSpacing() * 5f, true)
                    for (file in files) {
                        if (ImGui.selectable(file.fileName.toString())) { path.set(file.toAbsolutePath().toString()); confirm = false; error = "" }
                    }
                    ImGui.endChild()
                }
            } catch (_: Exception) { }
        }
        if (error.isNotEmpty()) ImGui.textWrapped(error)
        if (confirm) ImGui.textWrapped(if (save) I18n.get("cobblemon.cinematic_editor.file.replace_confirmation") else I18n.get("cobblemon.cinematic_editor.file.open_confirmation"))
        if (ImGui.button(if (confirm) I18n.get("cobblemon.cinematic_editor.file.confirm") else if (save) I18n.get("cobblemon.cinematic_editor.file.save") else I18n.get("cobblemon.cinematic_editor.file.open"))) {
            try {
                val target = Path.of(path.get()).toAbsolutePath().normalize()
                if (Files.isDirectory(target)) throw SceneFileException(I18n.get("cobblemon.cinematic_editor.file.choose_file"))
                if (!confirm && (if (save) Files.exists(target) else SceneEditorOverlay.activeScene?.isDirty == true)) {
                    confirm = true
                } else {
                    if (save) {
                        val scene = SceneEditorOverlay.activeScene ?: throw SceneFileException(I18n.get("cobblemon.cinematic_editor.file.no_scene"))
                        Files.createDirectories(target.parent)
                        SceneSerializer.saveToFile(scene, target)
                    } else {
                        if (!SceneEditorOverlay.openSceneFromFile(target)) throw SceneFileException(I18n.get("cobblemon.cinematic_editor.file.invalid_scene"))
                    }
                    ImGui.closeCurrentPopup()
                }
            } catch (exception: Exception) {
                Cobblemon.LOGGER.warn("Could not access cinematic scene file {}", path.get(), exception)
                error = (exception as? SceneFileException)?.message ?: I18n.get("cobblemon.cinematic_editor.file.access_failed")
                confirm = false
            }
        }
        ImGui.sameLine()
        if (ImGui.button(I18n.get("cobblemon.cinematic_editor.file.cancel"))) ImGui.closeCurrentPopup()
        ImGui.endPopup()
    }
}
