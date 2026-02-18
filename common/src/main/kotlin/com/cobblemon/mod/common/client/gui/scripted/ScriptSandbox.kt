/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.scripted

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.ResourceLimits
import org.graalvm.polyglot.Value

/**
 * Creates sandboxed GraalJS environments for scripted GUIs.
 *
 * Uses GraalJS polyglot API with deny-by-default sandboxing:
 * - No host access, IO, threads, native access, or class loading
 * - Statement limit prevents infinite loops (cumulative across all calls)
 */
object ScriptSandbox {

    private const val DEFAULT_STATEMENT_LIMIT = 10_000_000L

    data class SandboxedEnvironment(val context: Context, val state: Value)

    /**
     * Creates a new sandboxed GraalJS context.
     *
     * @param statementLimit Cumulative statement limit for the context lifetime
     * @return A [SandboxedEnvironment] containing the context and persistent state object
     */
    fun createSandboxedContext(statementLimit: Long = DEFAULT_STATEMENT_LIMIT): SandboxedEnvironment {
        val context = Context.newBuilder("js")
            .allowHostAccess(HostAccess.NONE)
            .allowIO(false)
            .allowCreateThread(false)
            .allowNativeAccess(false)
            .allowCreateProcess(false)
            .allowHostClassLookup { false }
            .allowHostClassLoading(false)
            .resourceLimits(
                ResourceLimits.newBuilder()
                    .statementLimit(statementLimit, null)
                    .build()
            )
            .option("engine.WarnInterpreterOnly", "false")
            .build()

        context.eval("js", "var __state = {};")
        val state = context.getBindings("js").getMember("__state")

        return SandboxedEnvironment(context, state)
    }
}
