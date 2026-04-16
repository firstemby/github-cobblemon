/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.models.blockbench

import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.entity.PosableEntity
import net.minecraft.client.model.EntityModel
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.Entity

interface PosableRenderStateBridge {
    val renderEntity: Entity?
    val renderPosableState: PosableState?
}

abstract class PosableEntityModel<T : Entity>(
    renderTypeFunc: (Identifier) -> RenderType = RenderTypes::entityCutoutCull
) : EntityModel<LivingEntityRenderState>(ModelPart(emptyList(), emptyMap()), renderTypeFunc) {
    val context: RenderContext = RenderContext().also {
        it.put(RenderContext.RENDER_STATE, RenderContext.RenderState.WORLD)
    }
    lateinit var posableModel: PosableModel

    override fun setupAnim(state: LivingEntityRenderState) {
        if (!this::posableModel.isInitialized) {
            return
        }

        val bridgedState = state as? PosableRenderStateBridge
        val entity = if (bridgedState != null) {
            bridgedState.renderEntity ?: return
        } else {
            context.request(RenderContext.ENTITY)
        }
        val posableState = if (bridgedState != null) {
            bridgedState.renderPosableState ?: return
        } else {
            context.request(RenderContext.POSABLE_STATE) ?: return
        }

        if (bridgedState != null) {
            context.entity = entity
            context.put(RenderContext.POSABLE_STATE, posableState)
        }

        setupEntityTypeContext(entity)
        posableModel.applyAnimations(
            entity,
            posableState,
            state.walkAnimationPos,
            state.walkAnimationSpeed,
            getTicksForAnimation(entity, state.walkAnimationPos, state.walkAnimationSpeed, state.ageInTicks, state.yRot, state.xRot),
            state.yRot,
            state.xRot
        )
    }

    open fun getTicksForAnimation(
        entity: Entity?,
        limbSwing: Float,
        limbSwingAmount: Float,
        ageInTicks: Float,
        headYaw: Float,
        headPitch: Float
    ): Float = ageInTicks

    open fun setupEntityTypeContext(entity: Entity?) {
        context.entity = entity
        if (entity is PosableEntity) {
            context.put(RenderContext.POSABLE_STATE, entity.delegate as PosableState)
        }
    }
}
