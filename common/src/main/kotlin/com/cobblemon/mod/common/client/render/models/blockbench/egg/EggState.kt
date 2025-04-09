/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.models.blockbench.egg

import com.cobblemon.mod.common.api.scheduling.SchedulingTracker
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockActiveAnimation
import net.minecraft.core.BlockPos

/**
 * State for an egg.
 *
 * @author Hiroku
 * @since October 30th, 2023
 */
class EggState() : PosableState() {
    var totalPartialTicks = 0F
    var isHatching = false
    var hatchingTriggered = false
    var renderingBlockPos: BlockPos? = null
    var previousAnimationSeconds: Float = 0f

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
        setPose("stage0")
    }

    fun isHatchAnimationFinished(): Boolean {
        val animation = primaryAnimation ?: return false
        val animationName = (animation.animation as? BedrockActiveAnimation)?.animation?.name ?: return false

        return animationName == "animation.egg.hatch" &&
                animationSeconds >= animation.started + animation.duration
    }

    override fun getEntity() = null
    override fun updatePartialTicks(partialTicks: Float) {
        previousAnimationSeconds = animationSeconds
        currentPartialTicks += partialTicks / 2
        totalPartialTicks += partialTicks / 2
    }

    override val schedulingTracker = SchedulingTracker()
}