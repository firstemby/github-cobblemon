/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.layer

import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.render.item.HeldItemRenderer
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel
import com.cobblemon.mod.common.client.render.models.blockbench.pose.ModelPartTransformation
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.duck.PlayerDuck
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.pokemon.activestate.ShoulderedState
import com.cobblemon.mod.common.util.DataKeys
import com.cobblemon.mod.common.util.getUUID
import com.cobblemon.mod.common.util.hasUUID
import com.cobblemon.mod.common.util.isPokemonEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import java.util.UUID
import net.minecraft.client.Minecraft
import net.minecraft.client.model.player.PlayerModel
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.RenderLayer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import kotlin.jvm.optionals.getOrNull

class PokemonOnShoulderRenderer(
    renderLayerParent: RenderLayerParent<AvatarRenderState, PlayerModel>
) : RenderLayer<AvatarRenderState, PlayerModel>(renderLayerParent) {
    private val context = RenderContext().also {
        it.put(RenderContext.RENDER_STATE, RenderContext.RenderState.WORLD)
    }
    private val heldItemRenderer = HeldItemRenderer()

    private var leftState = FloatingState()
    private var lastRenderedLeft: ShoulderData? = null
    private var rightState = FloatingState()
    private var lastRenderedRight: ShoulderData? = null

    override fun submit(
        matrixStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        packedLight: Int,
        renderState: AvatarRenderState,
        yRot: Float,
        xRot: Float
    ) {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.level?.getEntity(renderState.id) as? Player ?: return
        renderShoulder(matrixStack, submitNodeCollector, packedLight, renderState, player, yRot, xRot, true)
        renderShoulder(matrixStack, submitNodeCollector, packedLight, renderState, player, yRot, xRot, false)
    }

    private fun configureState(state: FloatingState, model: PosableModel, leftShoulder: Boolean, shoulderData: ShoulderData): FloatingState {
        state.currentModel = model
        state.currentAspects = shoulderData.aspects
        state.setPoseToFirstSuitable(if (leftShoulder) PoseType.SHOULDER_LEFT else PoseType.SHOULDER_RIGHT)
        state.runtime.environment.query.addFunction("is_holding_item") {
            DoubleValue(
                shoulderData.shownItem.let {
                    !it.isEmpty && !it.`is`(CobblemonItemTags.WEARABLE_HAT_ITEMS) && !it.`is`(CobblemonItemTags.WEARABLE_FACE_ITEMS)
                }
            )
        }
        state.runtime.environment.query.addFunction("is_wearing_hat") {
            DoubleValue(shoulderData.shownItem.`is`(CobblemonItemTags.WEARABLE_HAT_ITEMS))
        }
        state.runtime.environment.query.addFunction("is_wearing_face") {
            DoubleValue(shoulderData.shownItem.`is`(CobblemonItemTags.WEARABLE_FACE_ITEMS))
        }
        return state
    }

    private fun renderShoulder(
        matrixStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        packedLight: Int,
        renderState: AvatarRenderState,
        player: Player,
        yRot: Float,
        xRot: Float,
        leftShoulder: Boolean
    ) {
        val shoulderTag = getShoulderEntity(player, leftShoulder)
        val cache = playerCache.getOrPut(player.uuid) { ShoulderCache() }
        val shoulderData = shoulderDataFrom(shoulderTag) ?: getLocalShoulderFallback(player, leftShoulder)
        if (shoulderData == null) {
            clearCachedShoulder(cache, leftShoulder)
            return
        }

        if (leftShoulder) {
            cache.lastKnownLeft = shoulderData
        } else {
            cache.lastKnownRight = shoulderData
        }

        val shoulderState = getRenderState(leftShoulder, shoulderData)
        shoulderState.currentAspects = shoulderData.aspects
        shoulderState.updatePartialTicks(Minecraft.getInstance().deltaTracker.realtimeDeltaTicks.takeIf { !Minecraft.getInstance().isPaused } ?: 0F)

        val speciesIdentifier = shoulderData.species.resourceIdentifier
        val model = shoulderState.currentModel ?: VaryingModelRepository.getPoser(speciesIdentifier, shoulderState)
        shoulderState.currentModel = model
        val texture = VaryingModelRepository.getTexture(speciesIdentifier, shoulderState)
        val scale = shoulderData.form.baseScale * shoulderData.scaleModifier
        val widthOffset = shoulderData.form.hitbox.width / 2 - 0.7
        val heightOffset = -1.5 * scale

        context.put(RenderContext.SPECIES, speciesIdentifier)
        context.put(RenderContext.ASPECTS, shoulderData.aspects)
        context.put(RenderContext.POSABLE_STATE, shoulderState)
        context.put(RenderContext.TEXTURE, texture)
        model.context = context

        matrixStack.pushPose()
        if (player.isCrouching) {
            matrixStack.mulPose(Axis.XP.rotation(0.4F))
            matrixStack.translate(0F, 0F, -0.15F)
        }
        matrixStack.translate(
            if (leftShoulder) -widthOffset else widthOffset,
            if (player.isCrouching) heightOffset + 0.2 else heightOffset,
            0.0
        )
        matrixStack.scale(scale, scale, scale)
        matrixStack.translate(0F, 1.5F, 0F)

        model.applyAnimations(
            entity = null,
            state = shoulderState,
            limbSwing = renderState.walkAnimationPos,
            limbSwingAmount = renderState.walkAnimationSpeed,
            ageInTicks = renderState.ageInTicks,
            headYaw = yRot,
            headPitch = xRot
        )

        val partSnapshot = snapshotRelevantParts(model)
        submitNodeCollector.submitCustomGeometry(matrixStack, RenderTypes.entityCutout(texture)) { pose, consumer ->
            val renderStack = PoseStack()
            renderStack.last().set(pose)
            restoreRelevantParts(partSnapshot)
            model.render(context, renderStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, -0x1)
            model.setDefault()
        }

        for (layer in VaryingModelRepository.getLayers(speciesIdentifier, shoulderState)) {
            val layerTexture = layer.texture?.invoke(shoulderState) ?: continue
            val layerRenderType =
                if (layer.scrolling != null) {
                    model.getScrollingLayer(layerTexture, layer.scrolling)
                } else {
                    model.getLayer(layerTexture, layer.emissive, layer.translucent, layer.translucent_cull)
                }
            val tint = layer.tint
            val tintColor =
                ((tint.w * 255).toInt() shl 24) or
                    ((tint.x * 255).toInt() shl 16) or
                    ((tint.y * 255).toInt() shl 8) or
                    (tint.z * 255).toInt()

            submitNodeCollector.submitCustomGeometry(matrixStack, layerRenderType) { pose, consumer ->
                val renderStack = PoseStack()
                renderStack.last().set(pose)
                restoreRelevantParts(partSnapshot)
                model.rootPart.render(renderStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, tintColor)
                model.setDefault()
            }
        }

        heldItemRenderer.renderOnModel(
            shoulderData.shownItem,
            shoulderState,
            matrixStack,
            submitNodeCollector,
            packedLight
        )

        model.setDefault()
        model.red = 1F
        model.green = 1F
        model.blue = 1F
        model.resetLayerContext()
        matrixStack.popPose()
    }

    private fun snapshotRelevantParts(model: PosableModel): List<ModelPartTransformation> {
        return model.relevantPartsByName.values.distinct().map(ModelPartTransformation::derive)
    }

    private fun restoreRelevantParts(snapshot: List<ModelPartTransformation>) {
        snapshot.forEach(ModelPartTransformation::set)
    }

    private fun getRenderState(leftShoulder: Boolean, shoulderData: ShoulderData): FloatingState {
        val shoulderState =
            if (leftShoulder && shoulderData != lastRenderedLeft) {
                leftState = FloatingState().also { state ->
                    configureState(state, VaryingModelRepository.getPoser(shoulderData.species.resourceIdentifier, state), true, shoulderData)
                }
                lastRenderedLeft = shoulderData
                leftState
            } else if (!leftShoulder && shoulderData != lastRenderedRight) {
                rightState = FloatingState().also { state ->
                    configureState(state, VaryingModelRepository.getPoser(shoulderData.species.resourceIdentifier, state), false, shoulderData)
                }
                lastRenderedRight = shoulderData
                rightState
            } else {
                if (leftShoulder) leftState else rightState
            }

        if (shoulderState.currentModel == null) {
            shoulderState.currentModel = VaryingModelRepository.getPoser(shoulderData.species.resourceIdentifier, shoulderState)
        }
        return shoulderState
    }

    private fun clearCachedShoulder(cache: ShoulderCache, leftShoulder: Boolean) {
        if (leftShoulder) {
            cache.lastKnownLeft = null
        } else {
            cache.lastKnownRight = null
        }
        clearRenderedShoulder(leftShoulder)
    }

    private fun clearRenderedShoulder(leftShoulder: Boolean) {
        if (leftShoulder) {
            lastRenderedLeft = null
            leftState = FloatingState()
        } else {
            lastRenderedRight = null
            rightState = FloatingState()
        }
    }

    private fun getShoulderEntity(player: Player, leftShoulder: Boolean): CompoundTag {
        val duck = player as? PlayerDuck ?: return CompoundTag()
        return if (leftShoulder) duck.getShoulderEntityLeft() else duck.getShoulderEntityRight()
    }

    private fun getLocalShoulderFallback(player: Player, leftShoulder: Boolean): ShoulderData? {
        val localPlayer = Minecraft.getInstance().player ?: return null
        if (player.uuid != localPlayer.uuid) {
            return null
        }

        val pokemon = CobblemonClient.storage.party
            .firstOrNull { candidate -> (candidate?.state as? ShoulderedState)?.isLeftShoulder == leftShoulder }
            ?: return null
        return ShoulderData(
            uuid = pokemon.uuid,
            species = pokemon.species,
            form = pokemon.form,
            aspects = pokemon.aspects,
            scaleModifier = pokemon.scaleModifier,
            shownItem = pokemon.heldItem
        )
    }

    private data class ShoulderCache(
        var lastKnownLeft: ShoulderData? = null,
        var lastKnownRight: ShoulderData? = null
    )

    data class ShoulderData(
        val uuid: UUID,
        val species: Species,
        val form: FormData,
        val aspects: Set<String>,
        val scaleModifier: Float,
        val shownItem: ItemStack
    )

    companion object {
        private val playerCache = hashMapOf<UUID, ShoulderCache>()

        private fun extractUuid(shoulderNbt: CompoundTag): UUID {
            return if (shoulderNbt.hasUUID(DataKeys.SHOULDER_UUID)) {
                shoulderNbt.getUUID(DataKeys.SHOULDER_UUID)
            } else {
                shoulderNbt.getCompoundOrEmpty(DataKeys.POKEMON).getUUID(DataKeys.POKEMON_UUID)
            }
        }

        private fun extractData(shoulderNbt: CompoundTag, pokemonUUID: UUID): ShoulderData? {
            if (!shoulderNbt.contains(DataKeys.SHOULDER_SPECIES)) {
                return Pokemon.CLIENT_CODEC.decode(NbtOps.INSTANCE, shoulderNbt.getCompoundOrEmpty(DataKeys.POKEMON))
                    .map { it.first }
                    .mapOrElse({
                        ShoulderData(
                            pokemonUUID,
                            it.species,
                            it.form,
                            it.aspects,
                            it.scaleModifier,
                            it.heldItem
                        )
                    }, { null })
            }

            val species = PokemonSpecies.getByIdentifier(Identifier.parse(shoulderNbt.getStringOr(DataKeys.SHOULDER_SPECIES, ""))) ?: return null
            val formName = shoulderNbt.getStringOr(DataKeys.SHOULDER_FORM, "")
            val form = species.forms.firstOrNull { it.name == formName } ?: species.standardForm
            val aspects = shoulderNbt.getListOrEmpty(DataKeys.SHOULDER_ASPECTS).mapNotNull { it.asString().orElse(null) }.toSet()
            val scaleModifier = shoulderNbt.getFloat(DataKeys.SHOULDER_SCALE_MODIFIER).orElse(1F)
            val shownItem = Minecraft.getInstance().level?.registryAccess()
                ?.let { registryAccess ->
                    ItemStack.OPTIONAL_CODEC.parse(
                        registryAccess.createSerializationContext(NbtOps.INSTANCE),
                        shoulderNbt.getCompoundOrEmpty(DataKeys.SHOULDER_ITEM)
                    ).result().getOrNull()
                }
                ?: ItemStack.EMPTY
            return ShoulderData(pokemonUUID, species, form, aspects, scaleModifier, shownItem)
        }

        @JvmStatic
        fun shoulderDataOf(player: Player): Pair<ShoulderData?, ShoulderData?> {
            val cache = playerCache[player.uuid] ?: return Pair(null, null)
            return Pair(cache.lastKnownLeft, cache.lastKnownRight)
        }

        @JvmStatic
        fun shoulderDataFrom(compoundTag: CompoundTag): ShoulderData? {
            if (!compoundTag.isPokemonEntity()) {
                return null
            }

            val uuid = extractUuid(compoundTag)
            return extractData(compoundTag, uuid)
        }
    }
}
