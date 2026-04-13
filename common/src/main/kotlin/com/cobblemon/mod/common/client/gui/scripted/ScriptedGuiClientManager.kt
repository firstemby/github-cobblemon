/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.scripted

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.gui.scripted.GuiMode
import com.cobblemon.mod.common.api.gui.scripted.ScriptedGuiRegistry
import com.cobblemon.mod.common.api.gui.scripted.widget.WidgetTree
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Client-side manager for active scripted GUI overlays and screens.
 */
object ScriptedGuiClientManager {

    private val activeOverlays = ConcurrentHashMap<UUID, ScriptedGuiOverlay>()
    private val activeScreens = ConcurrentHashMap<UUID, ScriptedGuiScreen>()

    /**
     * Opens a scripted GUI.
     */
    fun open(sessionId: UUID, guiId: ResourceLocation, mode: GuiMode, args: Map<String, String>, blocksInput: Boolean, exclusive: Boolean = false) {
        val definition = ScriptedGuiRegistry.getClientDefinition(guiId)
        if (definition == null) {
            Cobblemon.LOGGER.error("[ScriptedGuiClientManager] GUI definition '$guiId' not found in client registry")
            return
        }

        val rootDefinition = try {
            WidgetTree.parse(definition.rootJson, isClient = true)
        } catch (e: Exception) {
            Cobblemon.LOGGER.error("[ScriptedGuiClientManager] Failed to parse widget tree for '$guiId'", e)
            return
        }

        val widgetTree = ScriptedGuiWidgetTree(guiId, sessionId, rootDefinition, args, definition.variables)

        when (mode) {
            GuiMode.OVERLAY -> {
                val overlay = ScriptedGuiOverlay(sessionId, widgetTree, blocksInput, exclusive)
                activeOverlays[sessionId] = overlay
                Cobblemon.LOGGER.debug("[ScriptedGuiClientManager] Opened overlay GUI '$guiId' (session=$sessionId)")
            }
            GuiMode.SCREEN -> {
                val screen = ScriptedGuiScreen(sessionId, widgetTree)
                activeScreens[sessionId] = screen
                Minecraft.getInstance().execute {
                    Minecraft.getInstance().setScreen(screen)
                }
                Cobblemon.LOGGER.debug("[ScriptedGuiClientManager] Opened screen GUI '$guiId' (session=$sessionId)")
            }
        }
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

    /**
     * Updates arguments on a running GUI.
     */
    fun updateArgs(sessionId: UUID, args: Map<String, String>, variables: Map<String, Double> = emptyMap()) {
        val widgetTree = activeOverlays[sessionId]?.widgetTree ?: activeScreens[sessionId]?.widgetTree
        if (widgetTree == null) {
            Cobblemon.LOGGER.warn("[ScriptedGuiClientManager] updateArgs: no widget tree found for session $sessionId")
            return
        }
        Cobblemon.LOGGER.info("[ScriptedGuiClientManager] updateArgs: sessionId=$sessionId args=${args.keys} variables=$variables")
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
     * Called when a ScriptedGuiScreen closes itself (via ESC or close action).
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
        activeScreens.values.forEach { it.widgetTree.cleanup() }
        activeScreens.clear()
    }

    /** Returns true if any active overlay has the exclusive flag set. */
    fun hasExclusiveOverlay(): Boolean = activeOverlays.values.any { it.exclusive }

    fun hasActiveOverlays(): Boolean = activeOverlays.isNotEmpty()
    fun hasActiveScreens(): Boolean = activeScreens.isNotEmpty()
}
