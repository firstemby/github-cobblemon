/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.models.blockbench.egg

import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.api.scheduling.SchedulingTracker
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.cobblemon.mod.common.client.render.models.blockbench.animation.PrimaryAnimation
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockActiveAnimation
import com.cobblemon.mod.common.net.messages.server.breeding.TriggerEggHatchPacket
import net.minecraft.core.BlockPos

/**
 * State for an egg.
 *
 * @author Hiroku
 * @since October 30th, 2023
 */
class EggState : PosableState() {
    var totalPartialTicks = 0F
    var isHatching = false
    var hatchingTriggered = false
    var renderingBlockPos: BlockPos? = null
    var previousAnimationSeconds: Float = 0f
    var hatchStartTick = 0L
    private var hatchAnimationEnd = 0F
    private var lastEffectAge = 0

    init {
        age = (200F * Math.random()).toInt()
        currentPartialTicks = 0F
    }

    fun peekAge() : Int { // Need to find a way to do this with getAge that's not protected
        return this.age
    }

    fun resetHatchingState() {
        isHatching = false
        hatchingTriggered = false
        renderingBlockPos = null
        primaryAnimation = null
        hatchAnimationEnd = 0F
        setPose("stage0")
    }

    override fun getEntity() = null

    // try to get the animation to be synced properly between all clients and server by tracking age
    override fun updateAge(age: Int) {
        this.age = age
        if (age <= lastEffectAge)
            return
        val previousAge = if (age - lastEffectAge > 5) age - 1 else lastEffectAge
        lastEffectAge = age
        runEffects(null, previousAge / 20F, age / 20F)
    }

    fun startHatchAnimation(animation: PrimaryAnimation) {
        addPrimaryAnimation(animation)
        val startedSeconds = hatchStartTick.toInt() / 20F
        animation.started = startedSeconds
        (animation.animation as? BedrockActiveAnimation)?.startedSeconds = startedSeconds
        hatchAnimationEnd = startedSeconds + animation.duration
    }

    fun tick(gameTime: Long) {
        val age = gameTime.toInt()
        if (age > lastEffectAge) {
            val previousAge = if (age - lastEffectAge > 5) age - 1 else lastEffectAge
            lastEffectAge = age
            runEffects(null, previousAge / 20F, age / 20F)
        }

        // if the hatch animation is complete then make sure we send that hatch packet
        if (isHatching && !hatchingTriggered && (hatchAnimationEnd > 0F && gameTime.toInt() / 20F >= hatchAnimationEnd)) {
            hatchingTriggered = true
            renderingBlockPos?.let { CobblemonNetwork.sendToServer(TriggerEggHatchPacket(it)) }
            resetHatchingState()
        }
    }

    override fun updatePartialTicks(partialTicks: Float) {
        previousAnimationSeconds = animationSeconds
        currentPartialTicks = partialTicks
        totalPartialTicks += partialTicks / 2
    }

    override val schedulingTracker = SchedulingTracker()
}