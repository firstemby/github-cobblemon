package com.cobblemon.mod.common.api.storage.player.adapter

import com.cobblemon.mod.common.api.pokedex.scan.ScannedBlocksManager
import com.cobblemon.mod.common.api.storage.player.PlayerInstancedDataStoreTypes
import java.util.UUID

class ScannedBlocksNbtBackend : NbtBackedPlayerData<ScannedBlocksManager>("scanned_blocks", PlayerInstancedDataStoreTypes.SCANNED_BLOCKS) {
    override val codec = ScannedBlocksManager.CODEC
    override val defaultData = { uuid: UUID -> ScannedBlocksManager(uuid) }
}
