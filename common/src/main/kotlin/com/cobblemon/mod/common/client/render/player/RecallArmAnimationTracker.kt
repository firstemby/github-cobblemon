/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.player

import com.cobblemon.mod.common.client.battle.ClientBallDisplay
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.pokeball.PokeBall
import com.mojang.blaze3d.vertex.PoseStack
import java.util.UUID
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.ItemRenderer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3

object RecallArmAnimationTracker {
    private const val HOLD_SECONDS = 0.8
    private const val EASE_SECONDS = 0.15

    class RecallAnim(val ball: PokeBall, val startTimeMs: Long) {
        val ballState = ClientBallDisplay(ball, emptySet())
        var beamOrigin: Vec3? = null
    }

    private val activeRecalls = mutableMapOf<UUID, RecallAnim>()
    private val ballContext = RenderContext().also { it.put(RenderContext.RENDER_STATE, RenderContext.RenderState.WORLD) }

    fun start(playerUuid: UUID, ball: PokeBall) {
        activeRecalls[playerUuid] = RecallAnim(ball, System.currentTimeMillis())
    }

    fun get(playerUuid: UUID): RecallAnim? {
        val anim = activeRecalls[playerUuid] ?: return null
        if (secondsSince(anim) > HOLD_SECONDS) {
            activeRecalls.remove(playerUuid)
            return null
        }
        return anim
    }

    fun isRecalling(playerUuid: UUID) = get(playerUuid) != null

    fun raiseFactor(playerUuid: UUID): Float {
        val anim = get(playerUuid) ?: return 0F
        val elapsed = secondsSince(anim)
        val riseIn = Mth.clamp((elapsed / EASE_SECONDS).toFloat(), 0F, 1F)
        val fallOut = Mth.clamp(((HOLD_SECONDS - elapsed) / EASE_SECONDS).toFloat(), 0F, 1F)
        return minOf(riseIn, fallOut)
    }

    fun renderBall(anim: RecallAnim, poseStack: PoseStack, buffer: MultiBufferSource, packedLight: Int, partialTicks: Float) {
        val state = anim.ballState
        val model = VaryingModelRepository.getPoser(anim.ball.name, state)
        val texture = VaryingModelRepository.getTexture(anim.ball.name, state)
        state.updatePartialTicks(partialTicks)
        model.context = ballContext
        ballContext.put(RenderContext.ASPECTS, state.aspects)
        ballContext.put(RenderContext.POSABLE_STATE, state)
        model.applyAnimations(null, state, 0F, 0F, 0F, 0F, 0F)
        val consumer = ItemRenderer.getFoilBufferDirect(buffer, RenderType.entityCutout(texture), false, false)
        model.render(ballContext, poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, -0x1)
        model.resetLayerContext()
    }

    private fun secondsSince(anim: RecallAnim) = (System.currentTimeMillis() - anim.startTimeMs) / 1000.0
}
