package com.cobblemon.mod.common.integration.vivecraft

import net.minecraft.client.Minecraft
import org.vivecraft.api.VRAPI
import org.vivecraft.api.client.VRClientAPI
import org.vivecraft.client_vr.ClientDataHolderVR

class CobblemonVRClient {
    var pokeballSwingTracker: PokeballSwingTracker =
        PokeballSwingTracker(Minecraft.getInstance(), VRClient)

    fun init() {
        VRAPI.instance().register(pokeballSwingTracker)
    }

    companion object {
        val VRClient: VRClientAPI = VRClientAPI.instance()
        val VR: VRAPI = VRAPI.instance()
    }
}