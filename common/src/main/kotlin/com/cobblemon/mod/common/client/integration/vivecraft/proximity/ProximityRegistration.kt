package com.cobblemon.mod.common.client.integration.vivecraft.proximity

import org.vivecraft.api.data.VRBodyPart

// TODO: Make an PR to Vivecraft that adds this feature directly to Vivecraft
data class ProximityRegistration(
    val bodyA: VRBodyPart,
    val bodyB: VRBodyPart,
    val distanceThreshold: Float,
    val angleThreshold: Float = 180f,
    val listener: ProximityListener
)