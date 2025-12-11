package com.cobblemon.mod.common.client.integration.vivecraft.proximity

import com.cobblemon.mod.common.platform.events.ClientTickEvent
import com.cobblemon.mod.common.platform.events.PlatformEvents
import org.vivecraft.api.client.VRClientAPI
import java.util.function.Consumer
import kotlin.math.acos

/*TODO:
    Add a way for Listeners to add custom Conditions, Ex. isActive in the Swinger    
 */
class ProximityManager {
    private val registrations = mutableListOf<ProximityRegistration>()
    private val previousInProximity = mutableMapOf<ProximityRegistration, Boolean>()
    private val api: VRClientAPI = VRClientAPI.instance()

    fun register(registration: ProximityRegistration) {
        registrations += registration
        previousInProximity.putIfAbsent(registration, false)
    }

    fun unregister(registration: ProximityRegistration) {
        registrations -= registration
        previousInProximity.remove(registration)
    }

    fun clear() {
        registrations.clear()
        previousInProximity.clear()
    }

    fun onTick(tickEvent: ClientTickEvent.Post) {
        val vrPose = api.getPostTickWorldPose() ?: return

        for (reg in registrations) {
            val poseA = vrPose.getBodyPartData(reg.bodyA) ?: continue
            val poseB = vrPose.getBodyPartData(reg.bodyB) ?: continue

            val distance = poseA.pos.distanceTo(poseB.pos)

            val dot = poseA.dir.dot(poseB.dir).coerceIn(-1.0, 1.0)
            val angleDeg = Math.toDegrees(acos(dot)).toFloat()
            val angleOk = angleDeg <= reg.angleThreshold

            val nowInProximity = distance <= reg.distanceThreshold && angleOk
            val wasInProximity = previousInProximity[reg] ?: false

            if (!wasInProximity && nowInProximity) {
                reg.listener.onEnter(reg.bodyA, reg.bodyB, vrPose)
            } else if (wasInProximity && !nowInProximity) {
                reg.listener.onExit(reg.bodyA, reg.bodyB, vrPose)
            }

            if (nowInProximity) {
                reg.listener.onProximity(reg.bodyA, reg.bodyB, vrPose)
            }

            previousInProximity[reg] = nowInProximity
        }
    }

    companion object {
        private val INSTANCE = ProximityManager()

        fun init() {
            PlatformEvents.CLIENT_TICK_POST.subscribe(Consumer { tickEvent: ClientTickEvent.Post? ->
                INSTANCE.onTick(
                    tickEvent!!
                )
            })
        }

        fun register(registration: ProximityRegistration) = INSTANCE.register(registration)
        fun unregister(registration: ProximityRegistration) = INSTANCE.unregister(registration)
        fun clearAll() = INSTANCE.clear()
    }
}