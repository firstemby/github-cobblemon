/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.Cobblemon.LOGGER
import com.cobblemon.mod.common.showdown.runner.ShowdownBattleServer
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.CountDownLatch

class ShowdownThread : Thread("Cobblemon Showdown") {

    private val latch = CountDownLatch(1)

    private val whenReady : Queue<(ShowdownBattleServer) -> Unit> = LinkedList()

    fun launch() {
        this.start()
        this.latch.await()
        for (action in whenReady) {
            action(Cobblemon.showdownServer)
        }
    }

    fun queue(action: (ShowdownBattleServer) -> Unit) {
        if (this.latch.count == 0L) {
            action(Cobblemon.showdownServer)
        } else {
            this.whenReady.add(action)
        }
    }

    override fun run() {
        LOGGER.info("Starting showdown service...")
        Cobblemon.showdownServer.connect()
        LOGGER.info("Showdown has been started!")
        this.latch.countDown()
    }
}