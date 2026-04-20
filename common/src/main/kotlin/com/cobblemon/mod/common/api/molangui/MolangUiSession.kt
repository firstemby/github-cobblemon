/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMoLangValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.api.molangui.container.CloseItemsBehavior
import com.cobblemon.mod.common.api.molangui.container.MolangContainerMenu
import com.cobblemon.mod.common.api.molangui.container.ServerCollectionSetup
import com.cobblemon.mod.common.api.molangui.container.SlotLayout
import com.cobblemon.mod.common.api.molangui.container.SlotQueryFunctions
import com.cobblemon.mod.common.api.molangui.widget.WidgetDefinition
import com.cobblemon.mod.common.api.molangui.widget.WidgetTree
import com.cobblemon.mod.common.api.molangui.widget.WidgetType
import com.cobblemon.mod.common.net.messages.client.molangui.MolangUiClosePacket
import com.cobblemon.mod.common.net.messages.client.molangui.MolangUiOpenPacket
import com.cobblemon.mod.common.net.messages.client.molangui.MolangUiUpdatePacket
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.withQueryValue
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.MenuProvider
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Server-side representation of an active scripted GUI session for a specific player.
 */
class MolangUiSession(
    val sessionId: UUID,
    val guiId: ResourceLocation,
    val player: ServerPlayer,
    definition: MolangUiDefinition,
    val blockPos: BlockPos? = null,
    private val actionListeners: MutableList<(String, Map<String, String>) -> Unit> = mutableListOf(),
    private val closeListeners: MutableList<() -> Unit> = mutableListOf()
) {
    var containerMenu: MolangContainerMenu? = null
        internal set

    private var closed = false
    private val currentArgs = ConcurrentHashMap<String, String>(
        definition.parameters.mapValues { (_, param) -> param.default ?: "" }
    )

    val runtime: MoLangRuntime = MolangUiContext.createServerRuntime(
        guiId = guiId,
        sessionId = sessionId,
        player = player,
        parameters = definition.parameters.mapValues { (_, param) -> param.default ?: "" }
    )

    val rootWidget: WidgetDefinition = WidgetTree.parse(definition.rootJson, isClient = false)

    /**
     * Returns the current value of a session argument, or null if not set.
     */
    fun getCurrentArg(name: String): String? = currentArgs[name]
    fun getAllArgs(): Map<String, String> = currentArgs.toMap()

    /**
     * Sets arguments locally without sending a packet to the client.
     * Used by on_open scripts to set initial args before the GUI is opened.
     */
    fun setArgLocally(name: String, value: String) {
        currentArgs[name] = value
    }

    /**
     * Sends updated arguments to the client and tracks them server-side.
     */
    fun updateArgs(args: Map<String, String>) {
        if (closed) return
        currentArgs.putAll(args)
        MolangUiUpdatePacket(sessionId, args).sendToPlayer(player)
    }

    /**
     * Pushes variable and/or parameter updates to the client GUI.
     */
    fun pushUpdate(variables: Map<String, Double> = emptyMap(), parameters: Map<String, String> = emptyMap()) {
        if (closed) {
            Cobblemon.LOGGER.warn("[MolangUiSession] pushUpdate called on closed session $sessionId")
            return
        }
        Cobblemon.LOGGER.info("[MolangUiSession] pushUpdate: sessionId=$sessionId variables=$variables parameters=${parameters.keys}")
        MolangUiUpdatePacket(sessionId, parameters, variables).sendToPlayer(player)
    }

    /**
     * Closes this GUI session and notifies the client.
     */
    fun close() {
        if (closed) return
        closed = true
        MolangUiClosePacket(sessionId).sendToPlayer(player)
        closeListeners.forEach { it() }
        MolangUiSessionManager.removeSession(sessionId)
    }

    /**
     * Adds a listener for player actions from the GUI.
     */
    fun addActionListener(listener: (action: String, data: Map<String, String>) -> Unit) {
        actionListeners.add(listener)
    }

    /**
     * Adds a listener for when the GUI is closed (by server or client).
     */
    fun addCloseListener(listener: () -> Unit) {
        closeListeners.add(listener)
    }

    /**
     * Handles a widget event by finding the widget definition, setting MoLang event context,
     * and evaluating the event handler expression.
     */
    fun handleWidgetEvent(widgetId: String, eventType: String, data: Map<String, String>) {
        if (closed) return
        val widgetDef = findWidget(rootWidget, widgetId)
        if (widgetDef == null) {
            Cobblemon.LOGGER.debug("[MolangUiSession] Widget '$widgetId' not found in GUI '$guiId'")
            return
        }
        val handler = widgetDef.events[eventType]
        if (handler == null) {
            Cobblemon.LOGGER.debug("[MolangUiSession] No handler for event '$eventType' on widget '$widgetId' in GUI '$guiId'")
            return
        }

        // "action@smelt" syntax routes directly through the action router (manifest scripts, Kotlin handlers)
        if (handler.startsWith("action@")) {
            val actionName = handler.substringAfter("action@")
            MolangUiActionRouter.routeAction(this, player, actionName, data)
            return
        }

        MolangUiContext.setEventContext(runtime, widgetId, data)
        try {
            handler.asExpressionLike().resolve(runtime)
        } catch (e: Exception) {
            Cobblemon.LOGGER.error("[MolangUiSession] Error evaluating event '$eventType' handler on widget '$widgetId': ${e.message}", e)
        }
    }

    private fun findWidget(node: WidgetDefinition, targetId: String): WidgetDefinition? {
        if (node.id == targetId) return node
        for (child in node.children) {
            val result = findWidget(child, targetId)
            if (result != null) return result
        }
        return null
    }

    /** Called when an action is received from the client. */
    internal fun onAction(action: String, data: Map<String, String>) {
        if (closed) return
        actionListeners.forEach { it(action, data) }
    }

    /** Called when the client notifies that it closed the GUI. */
    internal fun onClientClosed() {
        if (closed) return
        closed = true
        closeListeners.forEach { it() }
        MolangUiSessionManager.removeSession(sessionId)
    }

    fun isClosed(): Boolean = closed
}

/**
 * Extracts [SlotLayout] definitions from all SLOT_GRID widgets in this widget tree,
 * keyed by the collection_name property.
 */
fun WidgetDefinition.extractSlotLayouts(): Map<String, SlotLayout> {
    val layouts = mutableMapOf<String, SlotLayout>()
    collectSlotLayouts(this, layouts)
    return layouts
}

private fun collectSlotLayouts(node: WidgetDefinition, layouts: MutableMap<String, SlotLayout>) {
    if (node.type == WidgetType.SLOT_GRID) {
        val collectionName = (node.properties["collection_name"]?.raw as? String) ?: return
        val dims = node.properties["grid_dimensions"]?.raw
        val offset = node.properties["offset"]?.raw
        val slotSize = node.properties["slot_size"]?.raw
        val spacing = node.properties["slot_spacing"]?.raw

        fun listToInts(raw: Any?, default1: Int, default2: Int): Pair<Int, Int> {
            if (raw is List<*> && raw.size >= 2) {
                return Pair((raw[0] as? Number)?.toInt() ?: default1, (raw[1] as? Number)?.toInt() ?: default2)
            }
            return Pair(default1, default2)
        }

        val (cols, rows) = listToInts(dims, 1, 1)
        val (offX, offY) = listToInts(offset, 0, 0)
        val (slotW, slotH) = listToInts(slotSize, 18, 18)
        val (spX, spY) = listToInts(spacing, 0, 0)

        layouts[collectionName] = SlotLayout(
            offsetX = offX, offsetY = offY,
            columns = cols, rows = rows,
            slotWidth = slotW, slotHeight = slotH,
            spacingX = spX, spacingY = spY
        )
    }
    for (child in node.children) {
        collectSlotLayouts(child, layouts)
    }
}

/**
 * Returns true if this widget tree contains any SLOT_GRID widgets.
 */
fun WidgetDefinition.hasSlotGrids(): Boolean {
    if (type == WidgetType.SLOT_GRID) return true
    return children.any { it.hasSlotGrids() }
}

/**
 * Server-side manager for active scripted GUI sessions.
 */
object MolangUiSessionManager {

    private val sessions = ConcurrentHashMap<UUID, MolangUiSession>()
    private val disabledGuis = ConcurrentHashMap<UUID, Set<String>>()

    fun setDisabledGuis(playerId: UUID, guiIds: Set<String>) {
        if (guiIds.isEmpty()) disabledGuis.remove(playerId)
        else disabledGuis[playerId] = guiIds
    }

    fun isGuiDisabled(playerId: UUID, guiId: ResourceLocation): Boolean {
        return disabledGuis[playerId]?.contains(guiId.toString()) == true
    }

    /**
     * Opens a scripted GUI for a player.
     *
     * @param player The target player
     * @param guiId The GUI definition resource location
     * @param args Initial arguments to pass to the GUI
     * @param actionListener Optional listener for player actions
     * @return The session, or null if the GUI definition doesn't exist
     */
    fun openGui(
        player: ServerPlayer,
        guiId: ResourceLocation,
        args: Map<String, String> = emptyMap(),
        blockPos: BlockPos? = null,
        actionListener: ((String, Map<String, String>) -> Unit)? = null
    ): MolangUiSession? {
        if (isGuiDisabled(player.uuid, guiId)) {
            Cobblemon.LOGGER.warn("[MolangUiSessionManager] GUI '$guiId' is disabled for player ${player.name.string} due to version mismatch")
            return null
        }

        val definition = MolangUiRegistry.getDefinition(guiId)
        if (definition == null) {
            Cobblemon.LOGGER.error("[MolangUiSessionManager] GUI definition '$guiId' not found")
            return null
        }

        val sessionId = UUID.randomUUID()
        val session = MolangUiSession(sessionId, guiId, player, definition, blockPos)

        if (actionListener != null) {
            session.addActionListener(actionListener)
        }

        sessions[sessionId] = session

        // Resolve manifest parameter defaults (supports Molang expressions for persistent data)
        val manifest = MolangUiManifestRegistry.getManifest(guiId)
        if (manifest != null) {
            val resolvedDefaults = resolveManifestDefaults(manifest, player)
            resolvedDefaults.forEach { (key, value) -> session.setArgLocally(key, value) }
        }

        // Execute on_open script from manifest if present
        if (manifest?.onOpen != null) {
            MolangUiActionRouter.executeOnOpen(manifest.onOpen, session, player)
        }

        // Merge caller args with any args set by defaults/on_open script
        val mergedArgs = session.getAllArgs().toMutableMap()
        mergedArgs.putAll(args) // caller args take priority

        // Container setup: detect slot_grid widgets and create container menu
        val hasContainer = session.rootWidget.hasSlotGrids()

        // Send our open packet BEFORE player.openMenu() — the client needs pendingContainerData
        // set before the vanilla ClientboundOpenScreenPacket triggers the MenuScreens factory.
        MolangUiOpenPacket(
            sessionId = sessionId,
            guiId = guiId,
            mode = definition.mode,
            args = mergedArgs,
            blocksInput = definition.blocksInput,
            exclusive = definition.exclusive,
            hasContainer = hasContainer,
            slotLayouts = if (hasContainer) session.rootWidget.extractSlotLayouts() else emptyMap()
        ).sendToPlayer(player)

        // Now open the container menu via vanilla MenuProvider (sends ClientboundOpenScreenPacket)
        // This MUST come after our packet so the client has pendingContainerData ready.
        if (hasContainer) {
            val slotLayouts = session.rootWidget.extractSlotLayouts()
            val configs = manifest?.collections ?: emptyMap()

            val collectionSetup = mutableListOf<ServerCollectionSetup>()
            val sessionOwned = mutableSetOf<String>()

            for ((name, config) in configs) {
                val layout = slotLayouts[name] ?: continue
                val container: Container = if (config.source == "block_entity" && blockPos != null) {
                    val be = player.level().getBlockEntity(blockPos)
                    if (be is Container) be
                    else SimpleContainer(config.size)
                } else {
                    sessionOwned.add(name)
                    SimpleContainer(config.size)
                }
                collectionSetup.add(ServerCollectionSetup(name, config, layout, container))
            }

            player.openMenu(object : MenuProvider {
                override fun getDisplayName() = Component.empty()
                override fun createMenu(containerId: Int, inv: Inventory, p: Player): AbstractContainerMenu {
                    val menu = MolangContainerMenu(containerId, inv)
                    menu.initializeServer(
                        collectionSetup = collectionSetup,
                        shiftClickRules = manifest?.shiftClickRules ?: emptyList(),
                        sessionOwned = sessionOwned,
                        closeItemsBehavior = manifest?.closeItemsBehavior ?: CloseItemsBehavior.DROP,
                        onSlotChange = { collName, slotIdx ->
                            val slotGridWidget = findSlotGridWidget(session.rootWidget, collName)
                            val actionName = slotGridWidget?.events?.get("on_slot_change")
                            if (actionName != null) {
                                MolangUiActionRouter.routeAction(
                                    session, session.player, actionName,
                                    mapOf("collection" to collName, "index" to slotIdx.toString())
                                )
                            }
                        }
                    )
                    session.containerMenu = menu
                    SlotQueryFunctions.register(session.runtime, menu)
                    return menu
                }
            })
        }

        Cobblemon.LOGGER.debug("[MolangUiSessionManager] Opened GUI '$guiId' for ${player.name.string} (session=$sessionId)")
        return session
    }

    /**
     * Closes a GUI session.
     */
    fun closeGui(sessionId: UUID) {
        sessions[sessionId]?.close()
    }

    /**
     * Handles an action from a client.
     *
     * Actions in `"widgetId:eventType"` format are routed to [MolangUiSession.handleWidgetEvent]
     * for MoLang evaluation. All actions are also forwarded to registered action listeners for
     * backward compatibility with code that opens GUIs with action listeners.
     */
    fun handleAction(sessionId: UUID, player: ServerPlayer, action: String, data: Map<String, String>) {
        val session = sessions[sessionId]
        if (session == null) {
            Cobblemon.LOGGER.debug("[MolangUiSessionManager] Action for unknown session $sessionId")
            return
        }
        if (session.player.uuid != player.uuid) {
            Cobblemon.LOGGER.warn("[MolangUiSessionManager] Action from wrong player for session $sessionId")
            return
        }
        // Route widget events (format: "widgetId:eventType") to MoLang handler
        val colonIndex = action.indexOf(':')
        if (colonIndex > 0) {
            val widgetId = action.substring(0, colonIndex)
            val eventType = action.substring(colonIndex + 1)
            session.handleWidgetEvent(widgetId, eventType, data)
        }
        // Always fire action listeners for backward compatibility
        session.onAction(action, data)
    }

    /**
     * Handles a client-closed notification.
     */
    fun handleClosed(sessionId: UUID, player: ServerPlayer) {
        val session = sessions[sessionId]
        if (session == null) return
        if (session.player.uuid != player.uuid) {
            Cobblemon.LOGGER.warn("[MolangUiSessionManager] Close from wrong player for session $sessionId")
            return
        }
        session.onClientClosed()
    }

    /**
     * Closes all GUI sessions for a player. Call on disconnect.
     */
    fun closeAllForPlayer(playerId: UUID) {
        sessions.values.filter { it.player.uuid == playerId }.forEach {
            it.close()
        }
        disabledGuis.remove(playerId)
    }

    /** Internal: removes a session from tracking. */
    internal fun removeSession(sessionId: UUID) {
        sessions.remove(sessionId)
    }

    /**
     * Recursively searches the widget tree for a SLOT_GRID widget with the given collection_name.
     */
    private fun findSlotGridWidget(node: WidgetDefinition, collectionName: String): WidgetDefinition? {
        if (node.type == WidgetType.SLOT_GRID && (node.properties["collection_name"]?.raw as? String) == collectionName) {
            return node
        }
        for (child in node.children) {
            val result = findSlotGridWidget(child, collectionName)
            if (result != null) return result
        }
        return null
    }

    fun getSession(sessionId: UUID): MolangUiSession? = sessions[sessionId]

    /**
     * Returns the first active session for the given player and GUI ID, or null if none exists.
     */
    fun getSessionForPlayer(player: ServerPlayer, guiId: ResourceLocation): MolangUiSession? {
        return sessions.values.firstOrNull { it.player.uuid == player.uuid && it.guiId == guiId && !it.isClosed() }
    }

    /**
     * Resolves manifest parameter defaults. If a default value looks like a Molang expression
     * (contains q., v., math., etc.), it's evaluated with the player context.
     * This allows persistent data loading: "default": "q.format(q.player.data('my_gui').my_value, 0)"
     */
    private fun resolveManifestDefaults(manifest: MolangUiManifest, player: ServerPlayer): Map<String, String> {
        val resolved = mutableMapOf<String, String>()
        for ((paramName, param) in manifest.parameters) {
            val defaultValue = param.default ?: continue
            if (com.cobblemon.mod.common.api.molangui.widget.WidgetProperty.isMolangExpression(defaultValue)) {
                try {
                    val runtime = com.bedrockk.molang.runtime.MoLangRuntime().setup()
                    runtime.withQueryValue("player", player.asMoLangValue())
                    val expression = defaultValue.asExpressionLike()
                    val result = expression.resolveString(runtime)
                    resolved[paramName] = result
                } catch (e: Exception) {
                    Cobblemon.LOGGER.error("[MolangUiSessionManager] Failed to resolve default for param '$paramName': $defaultValue", e)
                    resolved[paramName] = defaultValue
                }
            } else {
                resolved[paramName] = defaultValue
            }
        }
        return resolved
    }
}
