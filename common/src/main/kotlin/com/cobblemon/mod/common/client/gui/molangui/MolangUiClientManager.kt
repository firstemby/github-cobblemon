/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.molangui

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.molangui.GuiMode
import com.cobblemon.mod.common.api.molangui.MolangUiDefinition
import com.cobblemon.mod.common.api.molangui.MolangUiParameters
import com.cobblemon.mod.common.api.molangui.MolangUiRegistry
import com.cobblemon.mod.common.api.molangui.container.MolangContainerMenu
import com.cobblemon.mod.common.api.molangui.container.SlotLayout
import com.cobblemon.mod.common.client.net.molangui.MolangUiCompatibilityRequestHandler
import com.cobblemon.mod.common.net.messages.server.molangui.MolangUiClosedPacket
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory
import java.util.UUID

object MolangUiClientManager {
    private class Session(
        var definition: MolangUiDefinition,
        val tree: MolangUiWidgetTree,
        val slotLayouts: Map<String, SlotLayout>,
        val preparedAtNanos: Long = System.nanoTime()
    ) {
        var screen: Screen? = null
        val overlay = if (definition.mode == GuiMode.OVERLAY) {
            MolangUiOverlay(tree, definition.exclusive)
        } else null
    }

    private val sessions = linkedMapOf<UUID, Session>()
    private var pendingContainerId: UUID? = null

    fun open(
        sessionId: UUID, guiId: ResourceLocation, contractVersion: String, mode: GuiMode, args: Map<String, String>,
        exclusive: Boolean = false,
        hasContainer: Boolean = false, slotLayouts: Map<String, SlotLayout> = emptyMap()
    ) {
        if (sessionId in sessions) return
        tick()
        val definition = MolangUiRegistry.getDefinition(guiId)
        if (definition == null || definition.contractVersion != contractVersion || definition.mode != mode ||
            definition.exclusive != exclusive ||
            (hasContainer && mode != GuiMode.SCREEN)) {
            Cobblemon.LOGGER.error("Rejected incompatible Molang UI open for '{}'", guiId)
            notifyClosed(sessionId)
            return
        }
        if (mode == GuiMode.OVERLAY && sessions.values.count { it.overlay != null } >= 8) {
            notifyClosed(sessionId)
            return
        }
        val tree = try {
            MolangUiWidgetTree(guiId, sessionId, definition.root,
                MolangUiParameters.decode(definition.parameters, args), definition.variables)
        } catch (error: Exception) {
            Cobblemon.LOGGER.error("Failed to prepare Molang UI '{}'", guiId, error)
            notifyClosed(sessionId)
            return
        }
        if (mode == GuiMode.SCREEN) {
            sessions.filterValues { it.definition.mode == GuiMode.SCREEN }.keys.toList().forEach { close(it, true) }
        }
        val session = Session(definition, tree, slotLayouts.toMap())
        sessions[sessionId] = session
        if (mode == GuiMode.SCREEN) {
            if (hasContainer) {
                pendingContainerId = sessionId
            } else {
                val screen = MolangUiScreen(sessionId, tree)
                session.screen = screen
                val client = Minecraft.getInstance()
                client.execute {
                    if (sessions[sessionId] === session) client.setScreen(screen)
                }
            }
        }
    }

    fun createContainerScreen(menu: MolangContainerMenu, playerInventory: Inventory): AbstractContainerScreen<MolangContainerMenu> {
        tick()
        val id = pendingContainerId
        pendingContainerId = null
        val session = id?.let(sessions::get)
            ?: return RejectedMolangUiContainerScreen(menu, playerInventory)
        return try {
            menu.initializeClient(session.slotLayouts)
            MolangUiContainerScreen(menu, playerInventory, session.tree.sessionId, session.tree).also { session.screen = it }
        } catch (error: Exception) {
            Cobblemon.LOGGER.error("Failed to prepare Molang UI container '{}'", session.tree.guiId, error)
            close(session.tree.sessionId, true)
            RejectedMolangUiContainerScreen(menu, playerInventory)
        }
    }

    fun close(sessionId: UUID) = close(sessionId, false)

    private fun close(sessionId: UUID, notifyServer: Boolean) {
        val session = sessions.remove(sessionId) ?: return
        if (pendingContainerId == sessionId) pendingContainerId = null
        session.tree.cleanup()
        if (notifyServer) notifyClosed(sessionId)
        val screen = session.screen ?: return
        val client = Minecraft.getInstance()
        client.execute {
            if (client.screen === screen) {
                if (screen is MolangUiContainerScreen && client.player?.containerMenu === screen.menu) {
                    if (notifyServer) client.player?.closeContainer() else client.player?.clientSideCloseContainer()
                } else client.setScreen(null)
            }
        }
    }

    internal fun onScreenRemoved(sessionId: UUID, screen: Screen, tree: MolangUiWidgetTree) {
        tree.cleanup()
        if (sessions[sessionId]?.screen !== screen) return
        sessions.remove(sessionId)
        notifyClosed(sessionId)
    }

    fun updateArgs(sessionId: UUID, args: Map<String, String>, variables: Map<String, Double> = emptyMap()) {
        val session = sessions[sessionId] ?: return
        try {
            val parameters = MolangUiParameters.decode(session.definition.parameters, args)
            MolangUiParameters.validateVariables(variables)
            if (variables.isNotEmpty()) session.tree.updateVariables(variables)
            if (parameters.isNotEmpty()) session.tree.updateParameters(parameters)
        } catch (error: Exception) {
            Cobblemon.LOGGER.warn("Rejected invalid Molang UI update for '{}'", session.tree.guiId, error)
            close(sessionId, true)
        }
    }

    fun renderOverlays(guiGraphics: GuiGraphics, delta: Float) {
        sessions.values.toList().forEach { it.overlay?.render(guiGraphics, delta) }
    }

    fun cleanup() {
        val old = sessions.values.toList()
        sessions.clear()
        pendingContainerId = null
        old.forEach { it.tree.cleanup() }
    }

    fun hasExclusiveOverlay(): Boolean = sessions.values.any { it.overlay?.exclusive == true }

    fun onResourceReload() {
        for ((id, session) in sessions.toMap()) {
            val previous = session.definition
            val definition = MolangUiRegistry.getDefinition(session.tree.guiId)
            if (definition == null || definition.contractVersion != previous.contractVersion ||
                definition.mode != previous.mode ||
                definition.exclusive != previous.exclusive || definition.parameters != previous.parameters ||
                definition.actions != previous.actions) {
                close(id, true)
                continue
            }
            try {
                session.tree.rebuildFromDefinition(definition)
                session.definition = definition
            } catch (error: Exception) {
                Cobblemon.LOGGER.error("Failed to reload Molang UI '{}'", session.tree.guiId, error)
                close(id, true)
            }
        }
        MolangUiCompatibilityRequestHandler.respond()
    }

    fun tick(nowNanos: Long = System.nanoTime()) {
        val id = pendingContainerId ?: return
        val session = sessions[id] ?: return
        if (nowNanos - session.preparedAtNanos > 5_000_000_000L) close(id, true)
    }

    private fun notifyClosed(sessionId: UUID) {
        if (Minecraft.getInstance().connection == null) return
        try {
            MolangUiClosedPacket(sessionId).sendToServer()
        } catch (error: Exception) {
            Cobblemon.LOGGER.warn("Failed to notify server of closed Molang UI '{}'", sessionId, error)
        }
    }
}
