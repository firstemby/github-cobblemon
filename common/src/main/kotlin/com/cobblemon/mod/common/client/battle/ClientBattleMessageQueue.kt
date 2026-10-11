/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.battle

import net.minecraft.util.FormattedCharSequence
import net.minecraft.network.chat.Component

/**
 * A wrapper around the client battle message information. This is essentially an appending list of all
 * the battle messages the client knows about. It keeps track of where it is up to, so they must actively
 * progress the queue to see newer messages.
 *
 * @author Hiroku
 * @since June 24th, 2022
 */
class ClientBattleMessageQueue {
    var listeners = mutableListOf<(FormattedCharSequence) -> Unit>()
    private val messages = mutableListOf<FormattedCharSequence>()
    private val components = mutableListOf<Component>()
    private val componentListeners = mutableListOf<(Component) -> Unit>()

    fun addComponents(messages: Iterable<Component>) {
        messages.forEach { message ->
            components.add(message)
            componentListeners.toList().forEach { it(message) }
        }
    }

    fun subscribeComponents(listener: (Component) -> Unit): () -> Unit {
        componentListeners.add(listener)
        components.forEach(listener)
        return { componentListeners.remove(listener) }
    }

    fun add(messages: Iterable<FormattedCharSequence>) {
        this.messages.addAll(messages)
        listeners.forEach { listener -> messages.forEach(listener) }
    }

    fun subscribe(listener: (FormattedCharSequence) -> Unit) {
        this.listeners.add(listener)
        messages.forEach(listener)
    }
}
