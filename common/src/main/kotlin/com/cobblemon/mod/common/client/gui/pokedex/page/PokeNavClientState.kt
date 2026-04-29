/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.page

import com.cobblemon.mod.common.net.messages.client.pokenav.PokeNavSpawnDataPacket
import com.cobblemon.mod.common.net.messages.client.pokenav.PokeNavAreaScanDataPacket
import java.util.UUID
import net.minecraft.resources.ResourceLocation

object PokeNavClientState {
    var buckets: List<String> = emptyList()
        private set

    var selectedBucket: String = ""
        private set

    var entries: List<PokeNavSpawnDataPacket.SpawnChanceEntry> = emptyList()
        private set

    var areaScanRadius: Float = 50F
        private set

    var areaScanEntries: List<PokeNavAreaScanDataPacket.AreaScanEntry> = emptyList()
        private set

    var isSpawnLoading: Boolean = false
        private set

    var isAreaScanLoading: Boolean = false
        private set

    var lastUpdateEpochMillis: Long = 0L
        private set

    var trackedEntityUuid: UUID? = null
        private set

    var trackedSpeciesId: ResourceLocation? = null
        private set

    fun markSpawnLoading() {
        isSpawnLoading = true
    }

    fun markAreaScanLoading() {
        isAreaScanLoading = true
    }

    fun apply(packet: PokeNavSpawnDataPacket) {
        buckets = packet.buckets
        selectedBucket = packet.selectedBucket
        entries = packet.entries
        isSpawnLoading = false
        lastUpdateEpochMillis = System.currentTimeMillis()
    }

    fun apply(packet: PokeNavAreaScanDataPacket) {
        areaScanRadius = packet.scanRadius
        areaScanEntries = packet.entries
        isAreaScanLoading = false
        lastUpdateEpochMillis = System.currentTimeMillis()
    }

    fun startTracking(entityUuid: UUID, speciesId: ResourceLocation) {
        trackedEntityUuid = entityUuid
        trackedSpeciesId = speciesId
    }

    fun clearTracking() {
        trackedEntityUuid = null
        trackedSpeciesId = null
    }
}
