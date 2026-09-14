package com.cobblemon.mod.common.client.editor.cinematic

import com.cobblemon.mod.common.Cobblemon.LOGGER
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.api.cinematic.registry.SceneDeserializer
import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.battles.stadium.StadiumCinematic
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.SceneSerializer
import com.cobblemon.mod.common.client.imgui.CobblemonImGui
import com.cobblemon.mod.common.net.messages.client.stadium.StadiumCinematicPacket
import com.cobblemon.mod.common.net.messages.server.stadium.EditStadiumCinematicPacket
import com.cobblemon.mod.common.util.lang
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import java.util.UUID

object EditorStadiumBridge : ClientNetworkPacketHandler<StadiumCinematicPacket> {
    private data class Target(val pos: BlockPos, val id: UUID, val connection: Any, val level: Any) {
        fun matches(client: Minecraft) = client.connection === connection && client.level === level
    }
    private data class Pending(val id: UUID, val target: Target, val editor: EditorScene?, val generation: Int, val sent: Long)
    private var pending: Pending? = null
    private var target: Target? = null
    private var editor: EditorScene? = null
    private var revision = ""
    private val gson = GsonBuilder().registerTypeAdapter(Scene::class.java, SceneDeserializer).create()

    init {
        PlatformEvents.CLIENT_TICK_POST.subscribe { tick(it.client) }
    }

    fun tick(client: Minecraft, now: Long = System.nanoTime()) {
        if (target?.matches(client) == false || pending?.target?.matches(client) == false) {
            pending = null
            target = null
            editor = null
            revision = ""
            return
        }
        val request = pending ?: return
        if (now - request.sent >= 15_000_000_000L) {
            pending = null
            client.player?.displayClientMessage(lang("stadium.cinematic.request_timeout"), false)
        }
    }

    private fun destination(pos: BlockPos, id: UUID, client: Minecraft): Target? {
        val level = client.level ?: return null
        val connection = client.connection ?: return null
        return Target(pos.immutable(), id, connection, level)
    }

    fun open(pos: BlockPos, id: UUID, now: Long = System.nanoTime()) {
        val client = Minecraft.getInstance()
        tick(client, now)
        if (pending?.editor != null) return
        val destination = destination(pos, id, client) ?: return
        val request = UUID.randomUUID()
        pending = Pending(request, destination, null, 0, now)
        try {
            EditStadiumCinematicPacket(request, pos, id).sendToServer()
        } catch (exception: Exception) {
            pending = null
            LOGGER.warn("Could not request stadium cinematic", exception)
            client.player?.displayClientMessage(lang("stadium.cinematic.open_failed"), false)
        }
    }

    internal fun bindEditor(loaded: EditorScene, pos: BlockPos, id: UUID, client: Minecraft, version: String) {
        target = destination(pos, id, client)
        editor = loaded.takeIf { target != null }
        revision = version.takeIf { target != null }.orEmpty()
    }

    fun canApply() = target?.matches(Minecraft.getInstance()) == true &&
        editor != null && SceneEditorOverlay.activeScene === editor && pending == null

    fun apply() {
        if (!canApply()) return
        apply(editor ?: return)
    }

    internal fun apply(current: EditorScene, now: Long = System.nanoTime()) {
        val client = Minecraft.getInstance()
        tick(client, now)
        if (current !== editor || pending != null) return
        val destination = target?.takeIf { it.matches(client) } ?: return
        val request = UUID.randomUUID()
        runCatching {
            val source = SceneSerializer.toJson(current).toString()
            if (source.length > StadiumCinematic.MAX_LENGTH) {
                Minecraft.getInstance().player?.displayClientMessage(lang("stadium.cinematic.export_oversized"), false)
                return
            }
            pending = Pending(request, destination, current, current.editGeneration, now)
            EditStadiumCinematicPacket(request, destination.pos, destination.id, revision, source).sendToServer()
        }.onFailure {
            pending = null
            LOGGER.warn("Could not apply stadium cinematic", it)
            Minecraft.getInstance().player?.displayClientMessage(lang("stadium.cinematic.apply_failed"), false)
        }
    }

    override fun handle(packet: StadiumCinematicPacket, client: Minecraft) {
        tick(client)
        val request = pending?.takeIf { it.id == packet.requestId } ?: return
        pending = null
        if (!request.target.matches(client)) return
        if (!packet.success) { client.player?.displayClientMessage(packet.message, false); return }
        if (request.editor != null) {
            if (editor === request.editor) {
                revision = packet.revision
                if (request.editor.editGeneration == request.generation) request.editor.clearDirty()
            }
            client.player?.displayClientMessage(packet.message, false)
            return
        }
        if (SceneEditorOverlay.activeScene?.isDirty == true) {
            client.player?.displayClientMessage(lang("stadium.cinematic.save_current"), false)
            return
        }
        runCatching {
            val source = JsonParser.parseString(packet.source).asJsonObject
            val loaded = SceneSerializer.fromScene(gson.fromJson(source, Scene::class.java), sourceJson = source)
            SceneEditorOverlay.openScene(loaded, packet.origin)
            bindEditor(loaded, request.target.pos, request.target.id, client, packet.revision)
            client.setScreen(null)
            if (!CobblemonImGui.editorVisible) CobblemonImGui.toggle()
        }.onFailure {
            LOGGER.warn("Could not open stadium cinematic", it)
            client.player?.displayClientMessage(lang("stadium.cinematic.open_failed"), false)
        }
    }
}
