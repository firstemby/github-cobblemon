package com.cobblemon.mod.common.client.integration.vivecraft

import com.cobblemon.mod.common.client.integration.vivecraft.party.PartyOnArm

class CobblemonVRClient {

    companion object {
        fun init() {
            PartyOnArm.init()
        }
    }
}