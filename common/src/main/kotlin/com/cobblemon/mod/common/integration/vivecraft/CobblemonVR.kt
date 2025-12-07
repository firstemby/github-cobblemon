package com.cobblemon.mod.common.integration.vivecraft

import com.cobblemon.mod.common.client.integration.vivecraft.listener.PokeBallSwingListener
import com.cobblemon.mod.common.client.integration.vivecraft.listener.PokeRodSwingListener
import org.vivecraft.api.VRAPI

class CobblemonVR {

    fun init() {
        VRAPI.instance()
            .register(PokeBallSwingListener())

        VRAPI.instance()
            .register(PokeRodSwingListener())
    }
}