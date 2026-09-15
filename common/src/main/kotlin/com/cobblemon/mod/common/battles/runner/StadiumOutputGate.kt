/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/*
 * Copyright (C) 2026 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.runner

import java.util.UUID

class StadiumOutputGate(private val deliver: (UUID, String) -> Unit) {
    private val pending = mutableMapOf<UUID, ArrayDeque<String>>()

    @Synchronized fun begin(id: UUID) {
        check(id !in pending)
        pending[id] = ArrayDeque()
    }

    @Synchronized fun receive(id: UUID, message: String) {
        val queue = pending[id]
        if (queue == null) deliver(id, message) else {
            check(queue.size < 4096) { "Too much stadium startup output" }
            queue.add(message)
        }
    }

    @Synchronized fun commit(id: UUID) {
        val queue = pending[id] ?: return
        while (pending[id] === queue && queue.isNotEmpty()) deliver(id, queue.removeFirst())
        if (pending[id] === queue) pending.remove(id)
    }

    @Synchronized fun discard(id: UUID) { pending.remove(id) }
}
