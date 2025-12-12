package com.cobblemon.mod.common.client.integration.vivecraft

import com.cobblemon.mod.common.client.integration.vivecraft.party.PartyOnArm
import com.cobblemon.mod.common.client.integration.vivecraft.proximity.ProximityManager
import com.cobblemon.mod.common.client.integration.vivecraft.proximity.ProximityRegistration
import com.cobblemon.mod.common.client.integration.vivecraft.listener.proximity.PokeRodBaitListener
import org.vivecraft.api.data.VRBodyPart

class CobblemonVRClient {

    companion object {
        fun init() {
            PartyOnArm.init()
            ProximityManager.init()
            ProximityManager.register(
                ProximityRegistration(
                    bodyA = VRBodyPart.MAIN_HAND,
                    bodyB = VRBodyPart.OFF_HAND,
                    distanceThreshold = 0.25f,
                    angleThreshold = 120f,
                    listener = PokeRodBaitListener("Main-OffHand")
                )
            )
        }
    }
}