/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokedex.scanner

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokedex.scan.ScannableDefinitions
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer

/**
 * Records allow-listed items as "scanned" once they enter a player's inventory (so item pickup is
 * the trigger, since floating item entities can't show a scan icon). Polled on a throttled player
 * tick; [com.cobblemon.mod.common.api.pokedex.scan.ScannedDataManager.record] no-ops on duplicates.
 */
object ItemScanRecorder {
    private const val INTERVAL_TICKS = 20

    fun onPlayerTick(player: ServerPlayer) {
        if (player.tickCount % INTERVAL_TICKS != 0) return
        val data = Cobblemon.playerDataManager.getScannedData(player)
        val inventory = player.inventory
        for (i in 0 until inventory.containerSize) {
            val stack = inventory.getItem(i)
            if (stack.isEmpty) continue
            val bucket = ScannableDefinitions.itemBucket(stack.item) ?: continue
            data.record(bucket, BuiltInRegistries.ITEM.getKey(stack.item))
        }
    }
}
