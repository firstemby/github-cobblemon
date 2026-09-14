/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ChangeDamageImmunity
import java.time.Instant
import net.minecraft.network.chat.Component

object TakeoverWatchdog {

    data class LogEntry(
        val timestamp: Instant,
        val kind: Kind,
        val message: Component,
        val stackTrace: String?
    ) {
        enum class Kind { FORCED_EXIT, SAFE_MODE_APPLIED }
    }

    private const val MAX_LOG_ENTRIES = 32
    private val log: ArrayDeque<LogEntry> = ArrayDeque(MAX_LOG_ENTRIES)

    fun entries(): List<LogEntry> = log.toList()

    fun clear() { log.clear() }

    fun onEnterTakeover(playState: EditorPlayState, scene: EditorScene?) {
        if (playState.sessionSafeModeApplied) return
        if (scene == null) return
        if (scene.damageImmunityDuringTakeover) {
            playState.markSafeModeApplied()
            return
        }
        scene.execute(
            ChangeDamageImmunity(oldValue = false, newValue = true)
        )
        playState.markSafeModeApplied()
        recordSafeModeApplied(scene.id)
    }

    /** Only takeover errors are caught; other modes propagate failures normally. */
    fun guardTick(playState: EditorPlayState, block: () -> Unit) {
        if (playState.mode != EditorPlayMode.TAKEOVER) {
            block()
            return
        }
        try {
            block()
        } catch (t: Throwable) {
            recordForcedExit(t)
            playState.exitTakeover()
            Cobblemon.LOGGER.error(
                "[TakeoverWatchdog] Forced EDIT mode after exception during takeover tick",
                t
            )
        }
    }

    private fun recordForcedExit(t: Throwable) {
        append(
            LogEntry(
                timestamp = Instant.now(),
                kind = LogEntry.Kind.FORCED_EXIT,
                message = Component.translatable("cobblemon.cinematic_editor.watchdog.forced_exit_message"),
                stackTrace = t.stackTraceToString()
            )
        )
    }

    private fun recordSafeModeApplied(sceneId: String) {
        append(
            LogEntry(
                timestamp = Instant.now(),
                kind = LogEntry.Kind.SAFE_MODE_APPLIED,
                message = Component.translatable("cobblemon.cinematic_editor.watchdog.safe_mode_message", sceneId),
                stackTrace = null
            )
        )
    }

    private fun append(entry: LogEntry) {
        if (log.size >= MAX_LOG_ENTRIES) log.removeFirst()
        log.addLast(entry)
    }
}
