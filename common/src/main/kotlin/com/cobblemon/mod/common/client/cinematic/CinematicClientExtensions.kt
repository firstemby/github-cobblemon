package com.cobblemon.mod.common.client.cinematic

import com.cobblemon.mod.common.net.messages.client.editor.EditorPreviewAckS2CPacket
import com.cobblemon.mod.common.net.messages.client.editor.EditorPreviewStateS2CPacket
import com.cobblemon.mod.common.net.messages.client.editor.EditorRouteResultS2CPacket
import java.util.UUID
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics

object CinematicClientExtensions {
    const val API_VERSION = 1

    var suppressMainCamera: () -> Boolean = { false }
    var suppressCompositor: (UUID) -> Boolean = { false }
    var renderHud: (GuiGraphics) -> Unit = {}
    var previewAck: (EditorPreviewAckS2CPacket, Minecraft) -> Unit = { _, _ -> }
    var previewState: (EditorPreviewStateS2CPacket, Minecraft) -> Unit = { _, _ -> }
    var routeResult: (EditorRouteResultS2CPacket, Minecraft) -> Unit = { _, _ -> }

    fun requireApiVersion(version: Int) {
        require(version == API_VERSION) {
            "Incompatible cinematic client API: required $version, available $API_VERSION"
        }
    }
}
