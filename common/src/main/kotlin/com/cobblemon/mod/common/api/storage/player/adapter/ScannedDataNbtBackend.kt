package com.cobblemon.mod.common.api.storage.player.adapter

import com.cobblemon.mod.common.api.pokedex.scan.ScannedDataManager
import com.cobblemon.mod.common.api.storage.player.PlayerInstancedDataStoreTypes
import java.util.UUID

class ScannedDataNbtBackend : NbtBackedPlayerData<ScannedDataManager>("scanned_data", PlayerInstancedDataStoreTypes.SCANNED_DATA) {
    override val codec = ScannedDataManager.CODEC
    override val defaultData = { uuid: UUID -> ScannedDataManager(uuid) }
}
