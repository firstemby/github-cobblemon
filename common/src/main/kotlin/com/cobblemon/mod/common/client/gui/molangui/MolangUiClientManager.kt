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
import com.cobblemon.mod.common.api.molangui.container.SlotLayout
import com.cobblemon.mod.common.api.molangui.widget.WidgetTree
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Client-side manager for active MolangUi overlays and screens.
 */
object MolangUiClientManager {

    private val activeOverlays = ConcurrentHashMap<UUID, MolangUiOverlay>()
    private val activeScreens = ConcurrentHashMap<UUID, Screen>()

    private data class PendingContainerScreen(
        val sessionId: UUID,
        val widgetTree: MolangUiWidgetTree,
        val slotLayouts: Map<String, SlotLayout>
    )

    private var pendingContainerData: PendingContainerScreen? = null

    /**
     * Opens a scripted GUI.
     */
    fun open(
        sessionId: UUID, guiId: ResourceLocation, mode: GuiMode, args: Map<String, String>,
        blocksInput: Boolean, exclusive: Boolean = false,
        hasContainer: Boolean = false, slotLayouts: Map<String, SlotLayout> = emptyMap()
    ) {
        val definition = MolangUiRegistry.getDefinition(guiId)
        if (definition == null) {
            Cobblemon.LOGGER.error("[MolangUiClientManager] GUI definition '$guiId' not found in client registry")
            return
        }

        val rootDefinition = try {
            WidgetTree.parse(definition.rootJson, isClient = true)
        } catch (e: Exception) {
            Cobblemon.LOGGER.error("[MolangUiClientManager] Failed to parse widget tree for '$guiId'", e)
            return
        }

        val widgetTree = MolangUiWidgetTree(guiId, sessionId, rootDefinition, args, definition.variables)

        when (mode) {
            GuiMode.OVERLAY -> {
                val overlay = MolangUiOverlay(sessionId, widgetTree, blocksInput, exclusive)
                activeOverlays[sessionId] = overlay
                Cobblemon.LOGGER.debug("[MolangUiClientManager] Opened overlay GUI '$guiId' (session=$sessionId)")
            }
            GuiMode.SCREEN -> {
                if (hasContainer) {
                    pendingContainerData = PendingContainerScreen(sessionId, widgetTree, slotLayouts)
                    Cobblemon.LOGGER.debug("[MolangUiClientManager] Queued container screen GUI '$guiId' (session=$sessionId)")
                } else {
                    val screen = MolangUiScreen(sessionId, widgetTree)
                    activeScreens[sessionId] = screen
                    Minecraft.getInstance().execute {
                        Minecraft.getInstance().setScreen(screen)
                    }
                    Cobblemon.LOGGER.debug("[MolangUiClientManager] Opened screen GUI '$guiId' (session=$sessionId)")
                }
            }
        }
    }

    /**
     * Called by the MenuScreens factory when the vanilla container open packet fires.
     * Consumes the pending container data and creates the [MolangUiContainerScreen].
     */
    fun createContainerScreen(
        menu: MolangContainerMenu,
        playerInventory: Inventory
    ): MolangUiContainerScreen? {
        val pending = pendingContainerData ?: return null
        pendingContainerData = null

        val clientLayouts = pending.slotLayouts
            .filter { it.key != "player_inventory" && it.key != "player_hotbar" }
            .map { (name, layout) ->
                ClientCollectionLayout(name, CollectionConfig(size = layout.slotCount), layout)
            }
        menu.initializeClient(clientLayouts)

        val screen = MolangUiContainerScreen(menu, playerInventory, pending.sessionId, pending.widgetTree)
        activeScreens[pending.sessionId] = screen
        return screen
    }

    /**
     * Closes a scripted GUI by session ID.
     */
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

    /**
     * Updates arguments on a running GUI.
     */
    fun updateArgs(sessionId: UUID, args: Map<String, String>, variables: Map<String, Double> = emptyMap()) {
        val widgetTree = activeOverlays[sessionId]?.widgetTree ?: activeScreens[sessionId]?.widgetTreeOrNull()
        if (widgetTree == null) {
            Cobblemon.LOGGER.warn("[MolangUiClientManager] updateArgs: no widget tree found for session $sessionId")
            return
        }
        Cobblemon.LOGGER.info("[MolangUiClientManager] updateArgs: sessionId=$sessionId args=${args.keys} variables=$variables")
        // Apply variables BEFORE parameters, since updateParameters triggers rebuildInstances
        // which evaluates visibility — variables must be set first for v.page etc. to work
        if (variables.isNotEmpty()) widgetTree.updateVariables(variables)
        if (args.isNotEmpty()) widgetTree.updateParameters(args)
    }

    /**
     * Renders all active overlays. Called from CobblemonClient.beforeChatRender().
     */
    fun renderOverlays(guiGraphics: GuiGraphics, delta: Float) {
        for ((_, overlay) in activeOverlays) {
            overlay.render(guiGraphics, delta)
        }
    }

    /**
     * Called when a MolangUiScreen closes itself (via ESC or close action).
     * Removes it from tracking without re-closing.
     */
    fun onScreenClosed(sessionId: UUID) {
        activeScreens.remove(sessionId)
    }

    /**
     * Cleans up all active GUIs. Called on disconnect.
     */
    fun cleanup() {
        activeOverlays.values.forEach { it.close() }
        activeOverlays.clear()
        activeScreens.values.forEach { it.widgetTreeOrNull()?.cleanup() }
        activeScreens.clear()
    }

    /** Returns true if any active overlay has the exclusive flag set. */
    fun hasExclusiveOverlay(): Boolean = activeOverlays.values.any { it.exclusive }

    fun hasActiveOverlays(): Boolean = activeOverlays.isNotEmpty()
    fun hasActiveScreens(): Boolean = activeScreens.isNotEmpty()

    /**
     * Called after resource pack reload (F3+T) to hot-reload active GUIs
     * with new definitions while preserving session state.
     */
    fun onResourceReload() {
        // Refresh overlays
        for ((sessionId, overlay) in activeOverlays.toMap()) {
            val newDef = MolangUiRegistry.getDefinition(overlay.widgetTree.guiId)
            if (newDef == null) {
                close(sessionId)
                Cobblemon.LOGGER.warn("[MolangUiClientManager] Molang UI definition removed during reload: ${overlay.widgetTree.guiId}")
                continue
            }
            overlay.widgetTree.rebuildFromDefinition(newDef)
            Cobblemon.LOGGER.info("[MolangUiClientManager] Hot-reloaded overlay GUI '${overlay.widgetTree.guiId}' (session=$sessionId)")
        }

        // Refresh screens
        for ((sessionId, screen) in activeScreens.toMap()) {
            val widgetTree = screen.widgetTreeOrNull() ?: continue
            val newDef = MolangUiRegistry.getDefinition(widgetTree.guiId)
            if (newDef == null) {
                close(sessionId)
                Cobblemon.LOGGER.warn("[MolangUiClientManager] Molang UI definition removed during reload: ${widgetTree.guiId}")
                continue
            }
            widgetTree.rebuildFromDefinition(newDef)
            Cobblemon.LOGGER.info("[MolangUiClientManager] Hot-reloaded screen GUI '${widgetTree.guiId}' (session=$sessionId)")
        }
    }
}
