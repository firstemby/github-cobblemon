/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui

import com.bedrockk.molang.runtime.MoLangRuntime
import net.minecraft.core.BlockPos
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.molangui.container.CollectionSource
import com.cobblemon.mod.common.api.molangui.container.MolangContainerMenu
import com.cobblemon.mod.common.api.molangui.container.ServerCollectionSetup
import com.cobblemon.mod.common.net.messages.client.molangui.MolangUiClosePacket
import com.cobblemon.mod.common.net.messages.client.molangui.MolangUiOpenPacket
import com.cobblemon.mod.common.net.messages.client.molangui.MolangUiUpdatePacket
import com.cobblemon.mod.common.util.asExpressionLike
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

internal class MolangUiSession(
    override val sessionId: UUID,
    override val uiId: ResourceLocation,
    val player: ServerPlayer,
    val contract: MolangUiContract,
    initialParameters: Map<String, ParameterValue>,
    val blockPos: BlockPos?
) : MolangUiSessionHandle {
    override var state: SessionState = SessionState.OPENING
        private set
    private val parameters = initialParameters.toMutableMap()
    internal var containerMenu: MolangContainerMenu? = null
    private var clientWasNotified = false

    override fun updateParameters(parameters: Map<String, ParameterValue>): Boolean {
        assertServerThread()
        if (state != SessionState.ACTIVE || !validateParameterUpdate(parameters)) return false
        MolangUiUpdatePacket(sessionId, parameters.mapValues { it.value.asWireValue() }).sendToPlayer(player)
        this.parameters.putAll(parameters)
        return true
    }

    override fun updateVariables(variables: Map<String, Double>): Boolean {
        assertServerThread()
        if (state != SessionState.ACTIVE || variables.size > MolangUiPacketLimits.MAX_ACTION_FIELDS) return false
        try {
            MolangUiParameters.validateVariables(variables)
        } catch (error: IllegalArgumentException) {
            return false
        }
        MolangUiUpdatePacket(sessionId, emptyMap(), variables).sendToPlayer(player)
        return true
    }

    override fun close() = closeInternal(notifyClient = true, closeMenu = true)

    internal fun closeFromClient() = closeInternal(notifyClient = false, closeMenu = true)
    internal fun closeFromMenu() = closeInternal(notifyClient = true, closeMenu = false)

    internal fun activate() {
        assertServerThread()
        check(state == SessionState.OPENING)
        state = SessionState.ACTIVE
    }

    internal fun notifyOpened(hasContainer: Boolean) {
        check(state == SessionState.OPENING)
        clientWasNotified = true
        MolangUiOpenPacket(
            sessionId, uiId, contract.contractVersion, contract.mode,
            parameters.mapValues { it.value.asWireValue() }, contract.exclusive,
            hasContainer, contract.collections.mapValues { it.value.layout }
        ).sendToPlayer(player)
    }

    internal fun getParameter(name: String): ParameterValue? = parameters[name]

    private fun validateParameterUpdate(values: Map<String, ParameterValue>): Boolean =
        values.size <= MolangUiPacketLimits.MAX_ACTION_FIELDS && values.all { (name, value) ->
            contract.parameters[name]?.type == value.type && value.asWireValue().length <= MolangUiPacketLimits.MAX_VALUE_LENGTH
        }

    private fun closeInternal(notifyClient: Boolean, closeMenu: Boolean) {
        assertServerThread()
        if (state == SessionState.CLOSING || state == SessionState.CLOSED) return
        state = SessionState.CLOSING
        val menu = containerMenu
        containerMenu = null
        try {
            cleanup("notify client") {
                if (notifyClient && clientWasNotified) MolangUiClosePacket(sessionId).sendToPlayer(player)
            }
            cleanup("close container") {
                if (closeMenu && menu != null && player.containerMenu === menu) player.closeContainer()
            }
            cleanup("release items") { menu?.releaseOwnedItemsOnce(player) }
        } finally {
            state = SessionState.CLOSED
            MolangUiService.remove(sessionId)
        }
    }

    private inline fun cleanup(operation: String, action: () -> Unit) {
        try {
            action()
        } catch (error: Exception) {
            Cobblemon.LOGGER.error("Failed to $operation while closing Molang UI session $sessionId", error)
        }
    }

    private fun assertServerThread() {
        check(player.server.isSameThread) { "Molang UI sessions are confined to the server thread" }
    }
}

object MolangUiService {
    private const val MAX_OVERLAYS_PER_PLAYER = 8
    private val sessions = linkedMapOf<UUID, MolangUiSession>()
    private val disabled = mutableMapOf<UUID, Set<ResourceLocation>>()
    private val rateLimiters = mutableMapOf<UUID, MolangUiRateLimiter>()

    fun open(player: ServerPlayer, request: MolangUiOpenRequest): MolangUiOpenResult {
        check(player.server.isSameThread) { "MolangUiService.open must run on the server thread" }
        val contract = MolangUiContractRegistry.getContract(request.uiId)
            ?: return MolangUiOpenResult.Rejected(RejectionReason.UNKNOWN_UI, "Unknown Molang UI '${request.uiId}'")
        // Intentionally does not wait for the compatibility round-trip; the client re-validates on open.
        if (disabled[player.uuid]?.contains(request.uiId) == true) {
            return MolangUiOpenResult.Rejected(RejectionReason.INCOMPATIBLE_CLIENT, "Client view is missing or incompatible")
        }
        val parameters = resolveParameters(contract, request.parameters)
            ?: return MolangUiOpenResult.Rejected(RejectionReason.INVALID_PARAMETERS, "Parameters do not match the contract")
        val playerSessions = sessions.values.filter { it.player.uuid == player.uuid && it.state != SessionState.CLOSED }
        if (contract.mode == GuiMode.OVERLAY && playerSessions.count { it.contract.mode == GuiMode.OVERLAY } >= MAX_OVERLAYS_PER_PLAYER) {
            return MolangUiOpenResult.Rejected(RejectionReason.SESSION_LIMIT, "At most $MAX_OVERLAYS_PER_PLAYER overlays may be active")
        }
        val collectionSetup = try {
            createCollections(player, contract, request.blockPos)
                ?: return MolangUiOpenResult.Rejected(RejectionReason.INVALID_CONTAINER, "The bound block container is unavailable or invalid")
        } catch (error: Exception) {
            Cobblemon.LOGGER.error("Failed to prepare Molang UI '${request.uiId}'", error)
            return MolangUiOpenResult.Rejected(RejectionReason.INTERNAL_ERROR, error.message ?: "Unable to prepare UI")
        }
        val session = MolangUiSession(UUID.randomUUID(), request.uiId, player, contract, parameters, request.blockPos)
        sessions[session.sessionId] = session
        MolangUiDiagnostics.sessionOpened()
        return try {
            if (contract.mode == GuiMode.SCREEN) {
                playerSessions.filter { it.contract.mode == GuiMode.SCREEN }.forEach(MolangUiSession::close)
            }
            check(session.state == SessionState.OPENING) { "Molang UI closed while replacing its previous screen" }
            MolangUiActionRegistry.executeOnOpen(session)
            check(session.state == SessionState.OPENING) { "Molang UI closed during on_open" }
            val hasContainer = contract.collections.isNotEmpty()
            session.notifyOpened(hasContainer)
            check(session.state == SessionState.OPENING) { "Molang UI closed while notifying the client" }
            if (hasContainer) openContainer(session, collectionSetup)
            session.activate()
            MolangUiOpenResult.Opened(session)
        } catch (error: Exception) {
            Cobblemon.LOGGER.error("Failed to open Molang UI '${request.uiId}'", error)
            session.close()
            MolangUiOpenResult.Rejected(RejectionReason.INTERNAL_ERROR, error.message ?: "Unable to open UI")
        }
    }

    fun handleAction(sessionId: UUID, player: ServerPlayer, actionRaw: String, raw: Map<String, String>) {
        check(player.server.isSameThread)
        val session = sessions[sessionId]
        if (session == null || session.player.uuid != player.uuid || session.state != SessionState.ACTIVE) {
            MolangUiDiagnostics.actionRejected()
            return
        }
        if (!rateLimiters.getOrPut(sessionId) { MolangUiRateLimiter() }.tryAcquire()) {
            MolangUiDiagnostics.actionRejected()
            return
        }
        val action = runCatching { ActionKey(actionRaw) }.getOrNull()
        val definition = action?.let(session.contract.actions::get)
        if (action == null || definition == null || raw.keys != definition.parameters.keys || raw.size > MolangUiPacketLimits.MAX_ACTION_FIELDS) {
            MolangUiDiagnostics.actionRejected()
            return
        }
        val typed = linkedMapOf<String, ParameterValue>()
        for ((name, type) in definition.parameters) {
            val value = raw[name]
            if (value == null || value.length > MolangUiPacketLimits.MAX_VALUE_LENGTH) {
                MolangUiDiagnostics.actionRejected(); return
            }
            typed[name] = ParameterValue.parse(type, value) ?: run { MolangUiDiagnostics.actionRejected(); return }
        }
        try {
            MolangUiActionRegistry.dispatch(session, action, typed)
        } catch (error: Exception) {
            MolangUiDiagnostics.actionRejected()
            Cobblemon.LOGGER.error("Molang UI action '${action.value}' failed in session $sessionId", error)
        }
    }

    fun handleClosed(sessionId: UUID, player: ServerPlayer) {
        val session = sessions[sessionId] ?: return
        if (session.player.uuid == player.uuid) session.closeFromClient() else MolangUiDiagnostics.actionRejected()
    }

    fun applyCompatibility(player: ServerPlayer, clientVersions: Map<ResourceLocation, String>) {
        val incompatible = MolangUiContractRegistry.versions().filter { (id, version) -> clientVersions[id] != version }.keys
        if (incompatible.isEmpty()) disabled.remove(player.uuid) else disabled[player.uuid] = incompatible
        MolangUiDiagnostics.compatibilityFailures(incompatible.size.toLong())
        sessions.values.filter { it.player.uuid == player.uuid && it.uiId in incompatible }.toList().forEach(MolangUiSession::close)
    }

    fun requestCompatibility(player: ServerPlayer) = MolangUiContractRegistry.sync(player)

    fun closeAllForPlayer(playerId: UUID) {
        sessions.values.filter { it.player.uuid == playerId }.toList().forEach(MolangUiSession::close)
        disabled.remove(playerId)
    }

    fun closeAll() {
        sessions.values.toList().forEach(MolangUiSession::close)
        disabled.clear()
    }

    fun getSession(sessionId: UUID): MolangUiSessionHandle? = sessions[sessionId]
    fun getSessionForPlayer(player: ServerPlayer, id: ResourceLocation): MolangUiSessionHandle? =
        sessions.values.firstOrNull { it.player.uuid == player.uuid && it.uiId == id && it.state == SessionState.ACTIVE }
    fun activeSessionCount(): Int = sessions.size

    internal fun remove(sessionId: UUID) {
        if (sessions.remove(sessionId) != null) MolangUiDiagnostics.sessionClosed()
        rateLimiters.remove(sessionId)
    }

    private fun resolveParameters(contract: MolangUiContract, supplied: Map<String, ParameterValue>): Map<String, ParameterValue>? {
        if (supplied.keys.any { it !in contract.parameters }) return null
        val values = linkedMapOf<String, ParameterValue>()
        for ((name, definition) in contract.parameters) {
            val suppliedValue = supplied[name]
            val value = suppliedValue ?: when (val default = definition.default) {
                is ParameterDefault.Literal -> default.value
                is ParameterDefault.Molang -> runCatching {
                    val result = default.expression.asExpressionLike().resolveString(MoLangRuntime())
                    ParameterValue.parse(definition.type, result)
                }.getOrNull()
                null -> null
            }
            if (value == null) {
                if (definition.required) return null else continue
            }
            if (value.type != definition.type || value.asWireValue().length > MolangUiPacketLimits.MAX_VALUE_LENGTH) return null
            values[name] = value
        }
        return values
    }

    private fun createCollections(
        player: ServerPlayer,
        contract: MolangUiContract,
        blockPos: BlockPos?
    ): List<ServerCollectionSetup>? {
        val setups = mutableListOf<ServerCollectionSetup>()
        for ((name, config) in contract.collections) {
            val container: Container = when (config.source) {
                CollectionSource.SESSION -> SimpleContainer(config.size)
                CollectionSource.BLOCK_ENTITY -> {
                    val pos = blockPos ?: return null
                    if (player.distanceToSqr(pos.x + .5, pos.y + .5, pos.z + .5) > 64.0 || !player.level().hasChunk(pos.x shr 4, pos.z shr 4)) return null
                    val blockEntity = player.level().getBlockEntity(pos)
                    if (blockEntity !is Container) return null
                    if ((config.slots ?: (0 until config.size).toList()).any { it >= blockEntity.containerSize }) return null
                    blockEntity
                }
            }
            setups += ServerCollectionSetup(name, config, container)
        }
        return setups
    }

    private fun openContainer(session: MolangUiSession, setup: List<ServerCollectionSetup>) {
        val owned = setup.filter { it.config.source == CollectionSource.SESSION }.mapTo(mutableSetOf()) { it.name }
        val opened = session.player.openMenu(object : MenuProvider {
            override fun getDisplayName() = Component.empty()
            override fun createMenu(containerId: Int, inventory: Inventory, player: Player): AbstractContainerMenu =
                MolangContainerMenu(containerId, inventory).also { menu ->
                    check(session.state == SessionState.OPENING) { "Molang UI closed before creating its container" }
                    session.containerMenu = menu
                    menu.initializeServer(
                        setup, session.contract.shiftClickRules, owned, session.contract.closeItemsPolicy,
                        owner = session.player, session = session, blockPos = session.blockPos
                    )
                }
        })
        check(opened.isPresent) { "player rejected the Molang UI container menu" }
    }
}

internal class MolangUiRateLimiter(private var lastNanos: Long = System.nanoTime()) {
    private var tokens = 40.0
    fun tryAcquire(now: Long = System.nanoTime()): Boolean {
        val elapsed = (now - lastNanos).coerceAtLeast(0) / 1_000_000_000.0
        tokens = minOf(40.0, tokens + elapsed * 20.0)
        lastNanos = now
        if (tokens < 1.0) return false
        tokens -= 1.0
        return true
    }
}
