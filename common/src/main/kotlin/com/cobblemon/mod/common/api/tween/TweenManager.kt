/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.tween

/**
 * Manages the lifecycle of [Tickable] instances (tweens, track players, etc.).
 *
 * Two singletons are provided:
 * - [server] — ticked from SERVER_TICK_POST
 * - [client] — ticked from GameRendererMixin at render frame rate
 */
class TweenManager {

    private val tickables = mutableListOf<Tickable>()
    private val pending = mutableListOf<Tickable>()

    /** Safe to call during update (additions are deferred). */
    fun add(tickable: Tickable) {
        pending.add(tickable)
    }

    fun update(dt: Float) {
        if (pending.isNotEmpty()) {
            tickables.addAll(pending)
            pending.clear()
        }

        val iterator = tickables.iterator()
        while (iterator.hasNext()) {
            val tickable = iterator.next()
            if (tickable.isActive) {
                tickable.tick(dt)
            }
            if (!tickable.isActive) {
                iterator.remove()
            }
        }
    }

    fun clear() {
        tickables.forEach { it.cancel() }
        tickables.clear()
        pending.forEach { it.cancel() }
        pending.clear()
    }

    val size: Int get() = tickables.size + pending.size

    companion object {
        /** Ticked at tps rate. */
        val server = TweenManager()

        /** Ticked at render frame rate. */
        val client = TweenManager()
    }
}
