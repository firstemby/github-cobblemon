/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.storage.player.adapter

import com.cobblemon.mod.common.api.storage.player.PlayerInstancedDataStoreTypes
import com.cobblemon.mod.common.api.tms.TMMoveManager
import com.cobblemon.mod.common.util.adapters.CodecBackedAdapter
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.util.UUID

class TMMoveJsonBackend : JsonBackedPlayerDataStoreBackend<TMMoveManager>("tm_moves", PlayerInstancedDataStoreTypes.TM_MOVES) {
    override val gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .registerTypeAdapter(TMMoveManager::class.java, CodecBackedAdapter(TMMoveManager.CODEC))
        .create()

    override val classToken = TypeToken.get(TMMoveManager::class.java)
    override val defaultData = defaultDataFunc

    override fun initialize(store: TMMoveManager) {
        store.initialize()
    }

    companion object {
        val defaultDataFunc = { uuid: UUID -> TMMoveManager(uuid) }
    }
}
