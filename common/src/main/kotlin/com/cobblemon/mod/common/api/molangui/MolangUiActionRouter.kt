/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molangui

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.addFunctions
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMoLangValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.api.molangui.container.SlotQueryFunctions
import com.cobblemon.mod.common.api.scripting.CobblemonScripts
import com.cobblemon.mod.common.util.withQueryValue
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

data class ActionContext(
    val session: MolangUiSession,
    val player: ServerPlayer,
    val data: Map<String, String>
)

typealias ActionHandler = (ActionContext) -> Unit

/**
 * Routes GUI actions through a tiered handler system:
 * 1. Kotlin handlers registered via [register]
 * 2. Molang scripts referenced in the GUI manifest
 * 3. Legacy session action listeners
 */
object MolangUiActionRouter {
    private val logger = LoggerFactory.getLogger("MolangUiActionRouter")
    private val kotlinHandlers = ConcurrentHashMap<Pair<ResourceLocation, String>, ActionHandler>()

    fun register(guiId: String, actionName: String, handler: ActionHandler) {
        val id = ResourceLocation.tryParse(guiId) ?: return
        kotlinHandlers[id to actionName] = handler
    }

    fun register(guiId: ResourceLocation, actionName: String, handler: ActionHandler) {
        kotlinHandlers[guiId to actionName] = handler
    }

    fun routeAction(session: MolangUiSession, player: ServerPlayer, action: String, data: Map<String, String>) {
        val guiId = session.guiId
        val context = ActionContext(session, player, data)

        // 1. Check for Kotlin handler
        val kotlinHandler = kotlinHandlers[guiId to action]
        if (kotlinHandler != null) {
            kotlinHandler(context)
            return
        }

        // 2. Check manifest for .molang script
        val manifest = MolangUiManifestRegistry.getManifest(guiId)
        val manifestAction = manifest?.actions?.get(action)
        if (manifestAction?.handler != null) {
            executeMolangAction(manifestAction.handler, manifestAction.params, context)
            return
        }

        // 3. Fall back to session action listeners (legacy support)
        session.onAction(action, data)
    }

    /**
     * Executes the on_open script from a manifest. Sets args locally (no packet)
     * since the GUI hasn't been sent to the client yet.
     */
    fun executeOnOpen(scriptId: ResourceLocation, session: MolangUiSession, player: ServerPlayer) {
        val script = CobblemonScripts.scripts[scriptId]
        if (script == null) {
            logger.warn("Molang on_open script not found: $scriptId")
            return
        }

        val runtime = MoLangRuntime().setup()
        runtime.withQueryValue("player", player.asMoLangValue())

        // q.session.set_arg sets args locally (no packet sent — GUI isn't open yet)
        val sessionStruct = ObjectValue(session).addFunctions(mapOf(
            "arg" to java.util.function.Function { params ->
                val name = params.getString(0)
                StringValue(session.getCurrentArg(name) ?: "")
            },
            "set_arg" to java.util.function.Function { params ->
                val name = params.getString(0)
                val value = params.getString(1)
                session.setArgLocally(name, value)
                DoubleValue(1.0)
            }
        ))
        runtime.withQueryValue("session", sessionStruct)

        session.containerMenu?.let { menu ->
            SlotQueryFunctions.register(runtime, menu)
        }

        try {
            script.resolve(runtime, emptyMap())
        } catch (e: Exception) {
            logger.error("Error executing Molang on_open script $scriptId", e)
        }
    }

    private fun executeMolangAction(scriptId: ResourceLocation, paramNames: List<String>, context: ActionContext) {
        val script = CobblemonScripts.scripts[scriptId]
        if (script == null) {
            logger.warn("Molang action script not found: $scriptId")
            return
        }

        val runtime = MoLangRuntime().setup()

        // Register player as a query value (follows ScriptNPCInteractionConfiguration pattern)
        runtime.withQueryValue("player", context.player.asMoLangValue())

        // Register session as q.session with methods: arg(), update_arg(), update_variable(), close()
        val session = context.session
        val sessionStruct = ObjectValue(session).addFunctions(mapOf(
            "arg" to java.util.function.Function { params ->
                val name = params.getString(0)
                StringValue(session.getCurrentArg(name) ?: "")
            },
            "update_arg" to java.util.function.Function { params ->
                val name = params.getString(0)
                val value = params.getString(1)
                session.updateArgs(mapOf(name to value))
                DoubleValue(1.0)
            },
            "update_variable" to java.util.function.Function { params ->
                val name = params.getString(0)
                val value = params.getDouble(1)
                session.pushUpdate(variables = mapOf(name to value))
                DoubleValue(1.0)
            },
            "close" to java.util.function.Function<com.bedrockk.molang.runtime.MoParams, Any> {
                session.close()
                DoubleValue(1.0)
            }
        ))
        runtime.withQueryValue("session", sessionStruct)

        session.containerMenu?.let { menu ->
            SlotQueryFunctions.register(runtime, menu)
        }

        // Map positional action data to named params from the manifest
        // e.g. data={"0": "5"} + paramNames=["amount"] → v.amount = 5.0
        for ((index, paramName) in paramNames.withIndex()) {
            val rawValue = context.data[index.toString()] ?: continue
            val numericValue = rawValue.toDoubleOrNull()
            if (numericValue != null) {
                runtime.environment.setSimpleVariable(paramName, DoubleValue(numericValue))
            } else {
                runtime.environment.setSimpleVariable(paramName, StringValue(rawValue))
            }
        }

        // Execute the script
        try {
            script.resolve(runtime, emptyMap())
        } catch (e: Exception) {
            logger.error("Error executing Molang action script $scriptId", e)
        }
    }
}
