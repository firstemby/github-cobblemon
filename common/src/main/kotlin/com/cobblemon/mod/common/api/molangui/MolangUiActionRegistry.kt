/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.scripting.CobblemonScripts
import com.cobblemon.mod.common.util.asExpressionLike
import java.util.concurrent.ConcurrentHashMap

object MolangUiActionRegistry {
    private val handlers = ConcurrentHashMap<ActionKey, MolangUiActionHandler>()

    fun register(key: ActionKey, handler: MolangUiActionHandler) {
        check(handlers.putIfAbsent(key, handler) == null) { "Molang UI action '$key' is already registered" }
    }

    fun unregister(key: ActionKey): Boolean = handlers.remove(key) != null

    internal fun dispatch(
        session: MolangUiSession,
        action: ActionKey,
        parameters: Map<String, ParameterValue>
    ) {
        val definition = session.contract.actions[action]
            ?: throw IllegalArgumentException("undeclared action '${action.value}'")
        val registered = handlers[action]
        if (registered != null) {
            registered.handle(MolangUiActionContext(session, session.player, parameters))
            return
        }
        val scriptId = definition.handler
            ?: throw IllegalStateException("action '${action.value}' has no registered handler")
        val script = CobblemonScripts.scripts[scriptId]
            ?: throw IllegalStateException("Molang UI action script '$scriptId' was not loaded")
        val runtime = MolangUiServerRuntimeFactory.create(session)
        parameters.forEach { (name, value) -> runtime.environment.setSimpleVariable(name, value.asMoLangValue()) }
        script.resolve(runtime, emptyMap())
    }

    internal fun executeOnOpen(session: MolangUiSession) {
        val scriptId = session.contract.onOpen ?: return
        val script = CobblemonScripts.scripts[scriptId]
            ?: throw IllegalStateException("Molang UI on_open script '$scriptId' was not loaded")
        script.resolve(MolangUiServerRuntimeFactory.create(session), emptyMap())
    }
}
