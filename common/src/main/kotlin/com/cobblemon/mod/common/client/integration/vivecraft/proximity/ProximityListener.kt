package com.cobblemon.mod.common.client.integration.vivecraft.proximity

import org.vivecraft.api.data.VRBodyPart
import org.vivecraft.api.data.VRPose

interface ProximityListener {
    fun onEnter(bodyA: VRBodyPart, bodyB: VRBodyPart, pose: VRPose)
    fun onExit(bodyA: VRBodyPart, bodyB: VRBodyPart, pose: VRPose)
    fun onProximity(bodyA: VRBodyPart, bodyB: VRBodyPart, pose: VRPose)
}