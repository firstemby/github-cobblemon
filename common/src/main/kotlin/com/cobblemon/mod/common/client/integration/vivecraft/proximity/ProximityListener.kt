package com.cobblemon.mod.common.client.integration.vivecraft.proximity

import org.vivecraft.api.data.VRBodyPart
import org.vivecraft.api.data.VRPose

// TODO: Make an PR to Vivecraft that adds this feature directly to Vivecraft
interface ProximityListener {
    fun isActive(registration: ProximityRegistration): Boolean = true
    fun onEnter(bodyA: VRBodyPart, bodyB: VRBodyPart, pose: VRPose)
    fun onExit(bodyA: VRBodyPart, bodyB: VRBodyPart, pose: VRPose)
    fun onTick(bodyA: VRBodyPart, bodyB: VRBodyPart, pose: VRPose)
}