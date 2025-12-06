package com.cobblemon.mod.common.integration.vivecraft

import com.cobblemon.mod.common.integration.vivecraft.listener.PokeballSwingListener
import net.minecraft.client.Minecraft
import org.vivecraft.api.VRAPI
import org.vivecraft.api.client.VRClientAPI

class CobblemonVRClient {
    var pokeballSwingListener: PokeballSwingListener =
        PokeballSwingListener(Minecraft.getInstance(), VRClient)

    fun init() {
        VRAPI.instance().register(pokeballSwingListener)
    }

    companion object {
        val VRClient: VRClientAPI = VRClientAPI.instance()
        val VR: VRAPI = VRAPI.instance()
    }
}