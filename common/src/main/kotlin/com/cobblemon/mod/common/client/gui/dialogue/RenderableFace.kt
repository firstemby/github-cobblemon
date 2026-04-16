/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.dialogue

import com.bedrockk.molang.runtime.struct.QueryStruct
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.gui.drawDialoguePosablePortrait
import com.cobblemon.mod.common.api.gui.drawPosablePortrait
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.entity.NPCClientDelegate
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.cobblemon.mod.common.entity.PosableEntity
import com.cobblemon.mod.common.entity.npc.NPCEntity
import kotlin.math.atan
import java.util.UUID
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.resources.Identifier
import net.minecraft.util.LightCoordsUtil
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Some time of face that can be rendered in a dialogue.
 *
 * @author Hiroku
 * @since January 1st, 2024
 */
sealed interface RenderableFace {
    val isLeftSide: Boolean
    val struct: QueryStruct
    fun extractRenderState(guiGraphics: GuiGraphicsExtractor, partialTicks: Float)
}

class PlayerRenderableFace(val playerId: UUID, override val isLeftSide: Boolean) : RenderableFace {
    override val struct: QueryStruct = QueryStruct(hashMapOf())
    override fun extractRenderState(guiGraphics: GuiGraphicsExtractor, partialTicks: Float) {
        val entity = Minecraft.getInstance().level?.getPlayerByUUID(playerId) ?: return
        val pose = guiGraphics.pose()
        // All of the maths below is shamelessly stolen from InventoryScreen.renderEntityInInventory.
        // Keep the original fixed tilt so the portrait matches the 1.21.1 dialogue box exactly.
        val f = atan((-40 / 40.0f).toDouble()).toFloat()
        val g = atan((-10 / 40.0f).toDouble()).toFloat()
        val rotation = Quaternionf().rotateZ(Math.PI.toFloat())
        val cameraAngle = Quaternionf().rotateX(g * 20.0f * (Math.PI.toFloat() / 180))
        rotation.mul(cameraAngle)
        val oldBodyYaw = entity.yBodyRot
        val oldEntityYaw = entity.yRot
        val oldPitch = entity.xRot
        val oldPrevHeadYaw = entity.yHeadRotO
        val oldHeadYaw = entity.yHeadRot
        try {
            // Modifies the entity for rendering based on our f and g values.
            entity.yBodyRot = 180.0F + f * 20.0F
            entity.yRot = (180.0F + f * 40.0F) * if (isLeftSide) 1 else -1
            entity.xRot = 0F
            entity.yHeadRot = entity.yRot
            entity.yHeadRotO = entity.yRot

            val renderState = Minecraft.getInstance().entityRenderDispatcher.extractEntity(entity, 1.0F)
            val livingState = renderState as? LivingEntityRenderState
            val entityScale = livingState?.scale ?: 1.0F
            val translationY = (livingState?.boundingBoxHeight ?: 0.0F) / 2.0F + 0.55F
            renderState.lightCoords = LightCoordsUtil.pack(15, 15)
            val portraitX = (pose.m20() - DialogueScreen.PORTRAIT_WIDTH / 2.0F).toInt()
            val portraitY = pose.m21().toInt()
            guiGraphics.entity(
                renderState,
                37F / entityScale,
                Vector3f(0F, translationY, 0F),
                rotation,
                cameraAngle,
                portraitX,
                portraitY,
                portraitX + DialogueScreen.PORTRAIT_WIDTH,
                portraitY + DialogueScreen.PORTRAIT_HEIGHT
            )
        } finally {
            entity.yBodyRot = oldBodyYaw
            entity.yRot = oldEntityYaw
            entity.xRot = oldPitch
            entity.yHeadRotO = oldPrevHeadYaw
            entity.yHeadRot = oldHeadYaw
        }
    }
}

class ReferenceRenderableFace(val entity: PosableEntity, override val isLeftSide: Boolean): RenderableFace {
    val state = entity.delegate as PosableState
    override val struct: QueryStruct
        get() = state.runtime.environment.query
    override fun extractRenderState(guiGraphics: GuiGraphicsExtractor, partialTicks: Float) {
        val state = this.state
        val pose = guiGraphics.pose()
        if (state is PokemonClientDelegate) {
            state.currentAspects = state.currentEntity.pokemon.aspects
            drawPosablePortrait(
                identifier = state.currentEntity.pokemon.species.resourceIdentifier,
                contextScale = state.currentEntity.pokemon.form.baseScale,
                matrixStack = guiGraphics.pose(),
                state = state,
                reversed = !isLeftSide,
                partialTicks = 0F // It's already being rendered potentially so we don't need to tick the state.
            )
        } else if (state is NPCClientDelegate) {
            entity as NPCEntity
            state.currentAspects = entity.aspects
            val limbSwing = entity.walkAnimation.position(partialTicks)
            val limbSwingAmount = entity.walkAnimation.speed(partialTicks)
            drawDialoguePosablePortrait(
                context = guiGraphics,
                identifier = state.npcEntity.resourceIdentifier,
                matrixStack = pose,
                state = state,
                reversed = !isLeftSide,
                partialTicks = 0F, // It's already being rendered potentially so we don't need to tick the state.
                limbSwing = limbSwing,
                limbSwingAmount = limbSwingAmount,
                ageInTicks = entity.age.toFloat(),
            )
        }
    }
}

class ArtificialRenderableFace(
    val modelType: String,
    val identifier: Identifier,
    val aspects: Set<String>,
    override val isLeftSide: Boolean
): RenderableFace {
    val state = FloatingState()
    override val struct: QueryStruct
        get() = state.runtime.environment.query

    override fun extractRenderState(guiGraphics: GuiGraphicsExtractor, partialTicks: Float) {
        val state = this.state
        state.currentAspects = aspects
        if (modelType == "pokemon") {
            val species = PokemonSpecies.getByIdentifier(identifier) ?: run {
                Cobblemon.LOGGER.error("Unable to find species for $identifier for a dialogue face. Defaulting to first species.")
                PokemonSpecies.species.first()
            }
            drawPosablePortrait(
                identifier = species.resourceIdentifier,
                matrixStack = guiGraphics.pose(),
                contextScale = species.getForm(aspects).baseScale,
                state = state,
                reversed = !isLeftSide,
                partialTicks = partialTicks
            )
        } else if (modelType == "npc") {
            drawDialoguePosablePortrait(
                context = guiGraphics,
                identifier = identifier,
                matrixStack = guiGraphics.pose(),
                state = state,
                reversed = !isLeftSide,
                partialTicks = partialTicks
            )
        }
    }
}
