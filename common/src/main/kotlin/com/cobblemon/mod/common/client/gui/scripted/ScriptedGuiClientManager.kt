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

        val runtime = ScriptedGuiRuntime(sessionId, definition, args)
        if (!runtime.load()) {
            Cobblemon.LOGGER.error("[ScriptedGuiClientManager] Failed to load script for '$guiId'")
            return
        }

        when (mode) {
            GuiMode.OVERLAY -> {
                val overlay = ScriptedGuiOverlay(sessionId, runtime, blocksInput, exclusive)
                activeOverlays[sessionId] = overlay
                Cobblemon.LOGGER.debug("[ScriptedGuiClientManager] Opened overlay GUI '$guiId' (session=$sessionId)")
            }
            GuiMode.SCREEN -> {
                val screen = ScriptedGuiScreen(sessionId, runtime)
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
    fun updateArgs(sessionId: UUID, args: Map<String, String>) {
        activeOverlays[sessionId]?.runtime?.updateArgs(args)
        activeScreens[sessionId]?.runtime?.updateArgs(args)
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
     * Called when a ScriptedGuiScreen closes itself (via ESC or Lua close()).
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
        activeScreens.values.forEach { it.runtime.cleanup() }
        activeScreens.clear()
    }

    /** Returns true if any active overlay has the exclusive flag set. */
    fun hasExclusiveOverlay(): Boolean = activeOverlays.values.any { it.exclusive }

    fun hasActiveOverlays(): Boolean = activeOverlays.isNotEmpty()
    fun hasActiveScreens(): Boolean = activeScreens.isNotEmpty()
}
