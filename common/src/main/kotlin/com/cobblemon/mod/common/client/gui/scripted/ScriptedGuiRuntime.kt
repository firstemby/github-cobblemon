/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.scripted

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.gui.scripted.ArgConverter
import com.cobblemon.mod.common.api.gui.scripted.ScriptedGuiDefinition
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Value
import java.util.UUID

/**
 * Per-GUI JavaScript runtime instance. Manages the GraalJS environment lifecycle
 * and dispatches calls to JS lifecycle functions.
 *
 * JS script can define these optional functions:
 * - init(ctx, args) - called once on open
 * - render(ctx, args, delta) - called every frame
 * - onClick(ctx, args, mouseX, mouseY, button) - mouse click
 * - onScroll(ctx, args, mouseX, mouseY, amount) - scroll
 * - onKeyPress(ctx, args, keyCode, scanCode, modifiers) - key press
 * - onArgsUpdate(ctx, args) - server sent updated args
 * - onClose(ctx, args) - GUI closing
 */
class ScriptedGuiRuntime(
    val sessionId: UUID,
    val definition: ScriptedGuiDefinition,
    initialArgs: Map<String, String>
) {
    private val sandbox = ScriptSandbox.createSandboxedContext()
    private val context: Context = sandbox.context
    private val state: Value = sandbox.state
    private var args: Map<String, String> = initialArgs

    // Cached JS function references
    private var initFn: Value? = null
    private var renderFn: Value? = null
    private var onClickFn: Value? = null
    private var onScrollFn: Value? = null
    private var onKeyPressFn: Value? = null
    private var onArgsUpdateFn: Value? = null
    private var onCloseFn: Value? = null

    private var initialized = false
    private var hasError = false

    fun load(): Boolean {
        if (definition.scriptSource.isBlank()) {
            Cobblemon.LOGGER.warn("[ScriptedGuiRuntime] No script source for GUI '${definition.guiId}'")
            hasError = true
            return false
        }

        try {
            context.eval("js", definition.scriptSource)

            val bindings = context.getBindings("js")
            initFn = bindings.getMember("init")?.takeIf { it.canExecute() }
            renderFn = bindings.getMember("render")?.takeIf { it.canExecute() }
            onClickFn = bindings.getMember("onClick")?.takeIf { it.canExecute() }
            onScrollFn = bindings.getMember("onScroll")?.takeIf { it.canExecute() }
            onKeyPressFn = bindings.getMember("onKeyPress")?.takeIf { it.canExecute() }
            onArgsUpdateFn = bindings.getMember("onArgsUpdate")?.takeIf { it.canExecute() }
            onCloseFn = bindings.getMember("onClose")?.takeIf { it.canExecute() }

            return true
        } catch (e: PolyglotException) {
            Cobblemon.LOGGER.error("[ScriptedGuiRuntime] Error loading script for '${definition.guiId}': ${e.message}")
            hasError = true
            return false
        }
    }

    fun callInit(drawingContext: DrawingContext) {
        if (hasError || initialized) return
        initialized = true

        val fn = initFn ?: return
        try {
            val ctx = drawingContext.buildJsObject(state)
            val argsObj = ArgConverter.toJsObject(args, definition.parameters)
            fn.execute(ctx, argsObj)
        } catch (e: PolyglotException) {
            Cobblemon.LOGGER.error("[ScriptedGuiRuntime] Error in init() for '${definition.guiId}': ${e.message}")
            hasError = true
        }
    }

    fun callRender(drawingContext: DrawingContext, delta: Float) {
        if (hasError) return
        val fn = renderFn ?: return
        try {
            val ctx = drawingContext.buildJsObject(state)
            val argsObj = ArgConverter.toJsObject(args, definition.parameters)
            fn.execute(ctx, argsObj, delta.toDouble())
        } catch (e: PolyglotException) {
            Cobblemon.LOGGER.error("[ScriptedGuiRuntime] Error in render() for '${definition.guiId}': ${e.message}")
            hasError = true
        }
    }

    fun callOnClick(drawingContext: DrawingContext, mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (hasError) return false
        val fn = onClickFn ?: return false
        try {
            val ctx = drawingContext.buildJsObject(state)
            val argsObj = ArgConverter.toJsObject(args, definition.parameters)
            val result = fn.execute(ctx, argsObj, mouseX, mouseY, button)
            return result.isBoolean && result.asBoolean()
        } catch (e: PolyglotException) {
            Cobblemon.LOGGER.error("[ScriptedGuiRuntime] Error in onClick() for '${definition.guiId}': ${e.message}")
            hasError = true
            return false
        }
    }

    fun callOnScroll(drawingContext: DrawingContext, mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (hasError) return false
        val fn = onScrollFn ?: return false
        try {
            val ctx = drawingContext.buildJsObject(state)
            val argsObj = ArgConverter.toJsObject(args, definition.parameters)
            val result = fn.execute(ctx, argsObj, mouseX, mouseY, amount)
            return result.isBoolean && result.asBoolean()
        } catch (e: PolyglotException) {
            Cobblemon.LOGGER.error("[ScriptedGuiRuntime] Error in onScroll() for '${definition.guiId}': ${e.message}")
            hasError = true
            return false
        }
    }

    fun callOnKeyPress(drawingContext: DrawingContext, keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (hasError) return false
        val fn = onKeyPressFn ?: return false
        try {
            val ctx = drawingContext.buildJsObject(state)
            val argsObj = ArgConverter.toJsObject(args, definition.parameters)
            val result = fn.execute(ctx, argsObj, keyCode, scanCode, modifiers)
            return result.isBoolean && result.asBoolean()
        } catch (e: PolyglotException) {
            Cobblemon.LOGGER.error("[ScriptedGuiRuntime] Error in onKeyPress() for '${definition.guiId}': ${e.message}")
            hasError = true
            return false
        }
    }

    fun callOnArgsUpdate(drawingContext: DrawingContext) {
        if (hasError) return
        val fn = onArgsUpdateFn ?: return
        try {
            val ctx = drawingContext.buildJsObject(state)
            val argsObj = ArgConverter.toJsObject(args, definition.parameters)
            fn.execute(ctx, argsObj)
        } catch (e: PolyglotException) {
            Cobblemon.LOGGER.error("[ScriptedGuiRuntime] Error in onArgsUpdate() for '${definition.guiId}': ${e.message}")
            hasError = true
        }
    }

    fun callOnClose(drawingContext: DrawingContext) {
        if (hasError) return
        val fn = onCloseFn ?: return
        try {
            val ctx = drawingContext.buildJsObject(state)
            val argsObj = ArgConverter.toJsObject(args, definition.parameters)
            fn.execute(ctx, argsObj)
        } catch (e: PolyglotException) {
            Cobblemon.LOGGER.error("[ScriptedGuiRuntime] Error in onClose() for '${definition.guiId}': ${e.message}")
        }
    }

    fun updateArgs(newArgs: Map<String, String>) {
        args = ArgConverter.mergeArgs(args, newArgs)
    }

    fun getArgs(): Map<String, String> = args

    /** Closes the GraalJS context and releases resources. */
    fun cleanup() {
        try {
            context.close()
        } catch (e: Exception) {
            Cobblemon.LOGGER.debug("[ScriptedGuiRuntime] Error closing context for '${definition.guiId}': ${e.message}")
        }
    }
}
