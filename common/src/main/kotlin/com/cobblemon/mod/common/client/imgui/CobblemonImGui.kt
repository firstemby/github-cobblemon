package com.cobblemon.mod.common.client.imgui

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.client.editor.cinematic.EditorDiagnostics
import com.cobblemon.mod.common.client.editor.cinematic.EditorPlayMode
import com.cobblemon.mod.common.client.editor.cinematic.SceneEditorOverlay
import com.cobblemon.mod.common.client.editor.cinematic.viewport.EditorViewportState
import com.cobblemon.mod.common.client.keybind.keybinds.CameraEditorBinding
import imgui.ImGui
import imgui.internal.ImGuiContext
import imgui.extension.nodeditor.NodeEditor
import imgui.extension.nodeditor.NodeEditorConfig
import imgui.extension.nodeditor.NodeEditorContext
import imgui.flag.ImGuiConfigFlags
import java.nio.file.Files
import net.minecraft.client.Minecraft

object CobblemonImGui {
    private var initialized = false
    private var glfw: CobblemonImGuiGlfw? = null
    private var gl3: CobblemonImGuiGl3? = null
    private var context: ImGuiContext? = null
    private var editorConfig: NodeEditorConfig? = null
    private var editorContext: NodeEditorContext? = null

    var editorVisible = false
        private set

    var gamePassthrough = false

    fun toggle() {
        if (!initialized) init()
        if (!initialized) return
        editorVisible = !editorVisible
        if (!editorVisible) {
            SceneEditorOverlay.playState.setMode(
                EditorPlayMode.EDIT)
            EditorViewportState.deactivate()
            SceneEditorOverlay.onEditorClosed()
            SceneEditorOverlay.restorePlayerMovement()
        }
        gamePassthrough = false
        glfw?.setEditorActive(editorVisible)
        glfw?.setEditorVisible(editorVisible)
        updateMouseGrab()
    }

    fun togglePassthrough() {
        gamePassthrough = !gamePassthrough
        glfw?.setEditorActive(!gamePassthrough)
        updateMouseGrab()
    }

    private fun updateMouseGrab() {
        val mc = Minecraft.getInstance()
        if (editorVisible && !gamePassthrough) {
            mc.mouseHandler.releaseMouse()
        } else if (editorVisible && gamePassthrough) {
            mc.mouseHandler.grabMouse()
        } else {
            mc.mouseHandler.grabMouse()
        }
    }

    fun isActive() = initialized && editorVisible

    private fun init() {
        if (initialized) return
        try {
            val window = Minecraft.getInstance().window.window

            context = ImGui.createContext()
            // NavigateButtonIndex controls canvas pan; DragButtonIndex controls node dragging.
            val nodeEditorConfig = NodeEditorConfig()
            editorConfig = nodeEditorConfig
            nodeEditorConfig.navigateButtonIndex = 2
            nodeEditorConfig.contextMenuButtonIndex = 1
            editorContext = NodeEditor.createEditor(nodeEditorConfig)
            val io = ImGui.getIO()
            io.addConfigFlags(ImGuiConfigFlags.DockingEnable)
            val iniPath = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("cobblemon").resolve("cinematic-editor-imgui.ini")
            try {
                Files.createDirectories(iniPath.parent)
            } catch (_: Exception) {   }
            io.setIniFilename(iniPath.toAbsolutePath().toString())

            val glfwBackend = CobblemonImGuiGlfw()
            glfw = glfwBackend
            glfwBackend.init(window)
            glfwBackend.setOnTabPressed { togglePassthrough() }
            glfwBackend.setGameKeyPredicate { key, scancode -> CameraEditorBinding.matches(key, scancode) }
            val gl3Backend = CobblemonImGuiGl3()
            gl3 = gl3Backend
            gl3Backend.init()
            gl3Backend.newFrame()

            ImGuiTheme.apply()
            initialized = true
        } catch (e: Exception) {
            Cobblemon.LOGGER.error("Could not initialize the cinematic editor", e)
            dispose()
        } catch (e: LinkageError) {
            Cobblemon.LOGGER.error("Could not load the cinematic editor native libraries", e)
            dispose()
        }
    }

    fun newFrame() {
        if (!initialized) return
        glfw?.newFrame()
        gl3?.newFrame()
        ImGui.newFrame()
    }

    fun endFrame() {
        if (!initialized) return
        ImGui.render()
        gl3?.renderDrawData(ImGui.getDrawData())
        EditorDiagnostics.afterRender()
    }

    fun wantCaptureMouse(): Boolean = initialized && editorVisible && !gamePassthrough
    fun wantCaptureKeyboard(): Boolean = initialized && editorVisible && !gamePassthrough

    // Always finish the frame: an unbalanced ImGui frame crashes the next newFrame call.
    fun renderFrame() {
        if (!initialized) return
        newFrame()
        try {
            SceneEditorOverlay.render()
        } catch (error: Throwable) {
            Cobblemon.LOGGER.error("[CobblemonImGui] editor overlay render failed", error)
        } finally {
            endFrame()
        }
    }

    fun bindNodeEditor() {
        editorContext?.let { NodeEditor.setCurrentEditor(it) }
    }

    fun dispose() {
        val renderer = gl3
        val input = glfw
        val nodes = editorContext
        val config = editorConfig
        val imguiContext = context
        initialized = false
        editorVisible = false
        gamePassthrough = false
        glfw = null
        gl3 = null
        editorContext = null
        editorConfig = null
        context = null

        val cleanup = listOf<() -> Unit>(
            { SceneEditorOverlay.restorePlayerMovement() },
            { EditorViewportState.deactivate() },
            { SceneEditorOverlay.onEditorClosed() },
            { renderer?.dispose() },
            { input?.dispose() },
            { nodes?.let(NodeEditor::destroyEditor) },
            { config?.destroy() },
            { imguiContext?.let(ImGui::destroyContext) }
        )
        cleanup.forEach { release ->
            try {
                release()
            } catch (error: Exception) {
                Cobblemon.LOGGER.error("Could not release a cinematic editor resource", error)
            } catch (error: LinkageError) {
                Cobblemon.LOGGER.error("Could not release a cinematic editor native resource", error)
            }
        }
    }
}
