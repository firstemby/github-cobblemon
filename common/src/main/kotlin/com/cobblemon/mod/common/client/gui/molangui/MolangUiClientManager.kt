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
import com.cobblemon.mod.common.api.molangui.MolangUiRegistry
import com.cobblemon.mod.common.api.molangui.container.ClientCollectionLayout
import com.cobblemon.mod.common.api.molangui.container.CollectionConfig
import com.cobblemon.mod.common.api.molangui.container.MolangContainerMenu
import com.cobblemon.mod.common.api.molangui.container.ShiftClickRule
import com.cobblemon.mod.common.api.molangui.container.SlotLayout
import com.cobblemon.mod.common.api.molangui.widget.WidgetTree
import com.cobblemon.mod.common.client.net.molangui.MolangUiCompatibilityRequestHandler
import com.cobblemon.mod.common.net.messages.server.molangui.MolangUiClosedPacket
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object MolangUiClientManager {

    private val activeOverlays = ConcurrentHashMap<UUID, MolangUiOverlay>()
    private val activeScreens = ConcurrentHashMap<UUID, Screen>()

    private data class PendingContainerScreen(
        val sessionId: UUID,
        val widgetTree: MolangUiWidgetTree,
        val slotLayouts: Map<String, SlotLayout>,
        val preparedAtNanos: Long
    )

    private var pendingContainerData: PendingContainerScreen? = null

    fun open(
        sessionId: UUID, guiId: ResourceLocation, contractVersion: String, mode: GuiMode, args: Map<String, String>,
        blocksInput: Boolean, exclusive: Boolean = false,
        hasContainer: Boolean = false, slotLayouts: Map<String, SlotLayout> = emptyMap()
    ) {
        val definition = MolangUiRegistry.getDefinition(guiId)
        if (definition == null || definition.contractVersion != contractVersion || definition.mode != mode ||
            definition.blocksInput != blocksInput || definition.exclusive != exclusive) {
            Cobblemon.LOGGER.error("Rejected incompatible Molang UI open for '$guiId'")
            MolangUiClosedPacket(sessionId).sendToServer()
            return
        }
        val widgetTree = MolangUiWidgetTree(guiId, sessionId, definition.root, args, definition.variables)

        when (mode) {
            GuiMode.OVERLAY -> {
                if (activeOverlays.size >= 8) {
                    MolangUiClosedPacket(sessionId).sendToServer()
                    return
                }
                val overlay = MolangUiOverlay(sessionId, widgetTree, blocksInput, exclusive)
                activeOverlays[sessionId] = overlay
            }
            GuiMode.SCREEN -> {
                if (hasContainer) {
                    pruneStalePending()
                    pendingContainerData?.let { MolangUiClosedPacket(it.sessionId).sendToServer() }
                    pendingContainerData = PendingContainerScreen(sessionId, widgetTree, slotLayouts, System.nanoTime())
                } else {
                    activeScreens.keys.toList().forEach(::close)
                    val screen = MolangUiScreen(sessionId, widgetTree)
                    activeScreens[sessionId] = screen
                    Minecraft.getInstance().execute {
                        Minecraft.getInstance().setScreen(screen)
                    }
                }
            }
        }
    }

    fun createContainerScreen(
        menu: MolangContainerMenu,
        playerInventory: Inventory
    ): MolangUiContainerScreen? {
        pruneStalePending()
        val pending = pendingContainerData ?: return null
        pendingContainerData = null

        val clientLayouts = pending.slotLayouts
            .filter { it.key !in ShiftClickRule.PLAYER_SIDE_NAMES }
            .map { (name, layout) ->
                ClientCollectionLayout(name, CollectionConfig(size = layout.slotCount, layout = layout), layout)
            }
        menu.initializeClient(clientLayouts)

        val screen = MolangUiContainerScreen(menu, playerInventory, pending.sessionId, pending.widgetTree)
        activeScreens[pending.sessionId] = screen
        return screen
    }

    fun close(sessionId: UUID) {
        activeOverlays.remove(sessionId)?.close()
        activeScreens.remove(sessionId)?.let { screen ->
            Minecraft.getInstance().execute {
                if (Minecraft.getInstance().screen == screen) {
                    Minecraft.getInstance().setScreen(null)
                }
            }
        }
    }

    private fun Screen.widgetTreeOrNull(): MolangUiWidgetTree? = when (this) {
        is MolangUiScreen -> widgetTree
        is MolangUiContainerScreen -> widgetTree
        else -> null
    }

    fun updateArgs(sessionId: UUID, args: Map<String, String>, variables: Map<String, Double> = emptyMap()) {
        val widgetTree = activeOverlays[sessionId]?.widgetTree ?: activeScreens[sessionId]?.widgetTreeOrNull()
        if (widgetTree == null) {
            return
        }
        if (variables.isNotEmpty()) widgetTree.updateVariables(variables)
        if (args.isNotEmpty()) widgetTree.updateParameters(args)
    }

    fun renderOverlays(guiGraphics: GuiGraphics, delta: Float) {
        for ((_, overlay) in activeOverlays) {
            overlay.render(guiGraphics, delta)
        }
    }

    fun onScreenClosed(sessionId: UUID) {
        activeScreens.remove(sessionId)
    }

    fun cleanup() {
        activeOverlays.values.forEach { it.close() }
        activeOverlays.clear()
        activeScreens.values.forEach { it.widgetTreeOrNull()?.cleanup() }
        activeScreens.clear()
        pendingContainerData?.let { MolangUiClosedPacket(it.sessionId).sendToServer() }
        pendingContainerData = null
    }

    fun hasExclusiveOverlay(): Boolean = activeOverlays.values.any { it.exclusive }

    fun hasActiveOverlays(): Boolean = activeOverlays.isNotEmpty()
    fun hasActiveScreens(): Boolean = activeScreens.isNotEmpty()

    fun onResourceReload() {
        for ((sessionId, overlay) in activeOverlays.toMap()) {
            val newDef = MolangUiRegistry.getDefinition(overlay.widgetTree.guiId)
            if (newDef == null) {
                close(sessionId)
                Cobblemon.LOGGER.warn("[MolangUiClientManager] Molang UI definition removed during reload: ${overlay.widgetTree.guiId}")
                continue
            }
            overlay.widgetTree.rebuildFromDefinition(newDef)
        }

        for ((sessionId, screen) in activeScreens.toMap()) {
            val widgetTree = screen.widgetTreeOrNull() ?: continue
            val newDef = MolangUiRegistry.getDefinition(widgetTree.guiId)
            if (newDef == null) {
                close(sessionId)
                Cobblemon.LOGGER.warn("[MolangUiClientManager] Molang UI definition removed during reload: ${widgetTree.guiId}")
                continue
            }
            widgetTree.rebuildFromDefinition(newDef)
        }
        MolangUiCompatibilityRequestHandler.respond()
    }

    private fun pruneStalePending() {
        val pending = pendingContainerData ?: return
        if (System.nanoTime() - pending.preparedAtNanos > 5_000_000_000L) {
            pendingContainerData = null
            MolangUiClosedPacket(pending.sessionId).sendToServer()
        }
    }
}
