/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.block

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.block.habitat.HabitatBlockEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.ItemBlockRenderTypes
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.level.block.state.BlockState

class HabitatBlockRenderer(ctx: BlockEntityRendererProvider.Context) : BlockEntityRenderer<HabitatBlockEntity> {
    private companion object {
        const val SPECIES_CYCLE_TICKS = 60L
        const val VISUAL_Y_OFFSET = 0.22
        const val BASE_SPAWN_DELAY = 20
        // Extra per-renderer vertical tuning. Increase to raise the rendered model.
        const val MODEL_Y_ADJUST = .3
        // Extra global multiplier applied after the cage-fit algorithm.
        const val MODEL_SCALE_MULTIPLIER = 0.6F
        const val SPAWNER_X_TILT_DEGREES = -30.0F
        const val FIT_PADDING = 0.95F
        const val CAGE_MIN_XZ = 0.05
        const val CAGE_MAX_XZ = 0.95
        const val CAGE_MIN_Y = 0.50
        const val CAGE_MAX_Y = 0.98
        const val PARTICLE_POINTS_PER_TICK = 1
        const val PARTICLE_EMIT_INTERVAL_TICKS = 2L
    }

    private data class CachedDisplayPokemon(
        val level: ClientLevel,
        val speciesId: ResourceLocation,
        val pokemonEntity: PokemonEntity
    )

    private data class SpawnerSpinState(
        var spin: Double = 0.0,
        var oSpin: Double = 0.0,
        var spawnDelay: Int = BASE_SPAWN_DELAY,
        var lastTick: Long = Long.MIN_VALUE
    )

    private val cachedDisplayPokemon = mutableMapOf<Long, CachedDisplayPokemon>()
    private val spinStateByPos = mutableMapOf<Long, SpawnerSpinState>()
    private val lastParticleGameTimeByPos = mutableMapOf<Long, Long>()

    override fun render(
        entity: HabitatBlockEntity,
        partialTicks: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int
    ) {
        val player = Minecraft.getInstance().player
        val viewerHoldingHabitatBlock = (player?.mainHandItem ?: player?.offhandItem)?.item == CobblemonItems.HABITAT_BLOCK
        val stateToRender: BlockState = if (viewerHoldingHabitatBlock) entity.blockState else entity.mimickedState
        val level = entity.level ?: return
        val isSolidRender = if (viewerHoldingHabitatBlock) false else stateToRender.isSolidRender(level, entity.blockPos)
        val renderType = if (viewerHoldingHabitatBlock) {
            RenderType.cutout()
        } else {
            runCatching { ItemBlockRenderTypes.getChunkRenderType(stateToRender) }
                .getOrElse { if (isSolidRender) RenderType.solid() else RenderType.translucent() }
        }

        poseStack.pushPose()
        Minecraft.getInstance().blockRenderer.renderBatched(
            stateToRender,
            entity.blockPos,
            level,
            poseStack,
            bufferSource.getBuffer(renderType),
            false,
            level.random
        )
        poseStack.popPose()

        if (viewerHoldingHabitatBlock) {
            renderSpawnerVisuals(entity, partialTicks, poseStack, bufferSource, packedLight)
        }
    }

    private fun renderSpawnerVisuals(
        habitatEntity: HabitatBlockEntity,
        partialTicks: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int
    ) {
        val level = habitatEntity.level as? ClientLevel ?: return
        val posKey = habitatEntity.blockPos.asLong()
        val speciesIds = habitatEntity.displaySpeciesIds
        if (speciesIds.isEmpty()) {
            cachedDisplayPokemon.remove(posKey)
            spinStateByPos.remove(posKey)
            lastParticleGameTimeByPos.remove(posKey)
            return
        }

        val cycleIndex = ((level.gameTime / SPECIES_CYCLE_TICKS) % speciesIds.size.toLong()).toInt()
        val speciesId = speciesIds[cycleIndex]
        val pokemonEntity = getOrCreateDisplayPokemon(level, posKey, speciesId) ?: return

        val spinState = spinStateByPos.getOrPut(posKey) { SpawnerSpinState() }
        tickSpinState(spinState, level.gameTime)
        val interpolatedSpin = Mth.rotLerp(partialTicks, spinState.oSpin.toFloat(), spinState.spin.toFloat()) * 10.0F
        pokemonEntity.tickCount = (level.gameTime and Int.MAX_VALUE.toLong()).toInt()
        pokemonEntity.setPos(
            habitatEntity.blockPos.x + 0.5,
            habitatEntity.blockPos.y + VISUAL_Y_OFFSET + MODEL_Y_ADJUST + 0.4,
            habitatEntity.blockPos.z + 0.5
        )
        val entityLight = LevelRenderer.getLightColor(level, habitatEntity.blockPos.above())

        poseStack.pushPose()
        poseStack.translate(0.5, VISUAL_Y_OFFSET + MODEL_Y_ADJUST, 0.5)
        val scale = computeFittedModelScale(pokemonEntity)
        poseStack.translate(0.0, 0.4, 0.0)
        poseStack.mulPose(Axis.YP.rotationDegrees(interpolatedSpin))
        poseStack.translate(0.0, -0.2, 0.0)
        poseStack.mulPose(Axis.XP.rotationDegrees(SPAWNER_X_TILT_DEGREES))
        poseStack.scale(scale, scale, scale)

        val dispatcher = Minecraft.getInstance().entityRenderDispatcher
        dispatcher.setRenderShadow(false)
        dispatcher.render(pokemonEntity, 0.0, 0.0, 0.0, 0F, partialTicks, poseStack, bufferSource, entityLight)
        dispatcher.setRenderShadow(true)
        poseStack.popPose()

        emitSpawnerParticles(level, posKey, habitatEntity.blockPos.x, habitatEntity.blockPos.y, habitatEntity.blockPos.z)
    }

    private fun getOrCreateDisplayPokemon(level: ClientLevel, posKey: Long, speciesId: ResourceLocation): PokemonEntity? {
        val cached = cachedDisplayPokemon[posKey]
        if (cached != null && cached.level === level && cached.speciesId == speciesId) {
            return cached.pokemonEntity
        }

        val species = PokemonSpecies.getByIdentifier(speciesId) ?: return null
        val pokemon = Pokemon().apply {
            isClient = true
            this.species = species
            initialize()
        }
        val pokemonEntity = PokemonEntity(level, pokemon).apply {
            noPhysics = true
            setNoGravity(true)
        }
        cachedDisplayPokemon[posKey] = CachedDisplayPokemon(level, speciesId, pokemonEntity)
        return pokemonEntity
    }

    private fun emitSpawnerParticles(level: ClientLevel, posKey: Long, x: Int, y: Int, z: Int) {
        val gameTime = level.gameTime
        if (lastParticleGameTimeByPos[posKey] == gameTime || gameTime % PARTICLE_EMIT_INTERVAL_TICKS != 0L) {
            return
        }
        lastParticleGameTimeByPos[posKey] = gameTime

        val rng = ThreadLocalRandom.current()
        repeat(PARTICLE_POINTS_PER_TICK) {
            val px = x + rng.nextDouble(CAGE_MIN_XZ, CAGE_MAX_XZ)
            val py = y + rng.nextDouble(CAGE_MIN_Y, CAGE_MAX_Y)
            val pz = z + rng.nextDouble(CAGE_MIN_XZ, CAGE_MAX_XZ)
            level.addParticle(ParticleTypes.SMOKE, px, py, pz, 0.0, 0.0, 0.0)
            level.addParticle(ParticleTypes.FLAME, px, py, pz, 0.0, 0.0, 0.0)
        }
    }

    private fun computeFittedModelScale(pokemonEntity: PokemonEntity): Float {
        val rawWidth = pokemonEntity.bbWidth.coerceAtLeast(0.05F)
        val rawDepth = rawWidth
        val rawHeight = pokemonEntity.bbHeight.coerceAtLeast(0.05F)

        // Volume-based baseline shrink so bulkier mons are reduced by their full hitbox mass,
        // rather than only the single largest dimension.
        var vanillaScale = 0.53125F
        val hitboxVolume = rawWidth * rawDepth * rawHeight
        if (hitboxVolume > 1.0F) {
            vanillaScale /= hitboxVolume.pow(1F / 3F)
        }

        // Fit to upper-cage bounds so large mons stay inside the top half.
        val availableWidth = (CAGE_MAX_XZ - CAGE_MIN_XZ).toFloat()
        val availableDepth = (CAGE_MAX_XZ - CAGE_MIN_XZ).toFloat()
        val availableHeight = (CAGE_MAX_Y - CAGE_MIN_Y).toFloat()

        val tiltRadians = Math.toRadians(abs(SPAWNER_X_TILT_DEGREES.toDouble()))
        val c = cos(tiltRadians).toFloat()
        val s = sin(tiltRadians).toFloat()

        // Conservative AABB of the model after the vanilla spawner X-tilt.
        val tiltedDepth = rawDepth * c + rawHeight * s
        val tiltedHeight = rawHeight * c + rawDepth * s

        val fitScaleX = availableWidth / rawWidth
        val fitScaleZ = availableDepth / tiltedDepth.coerceAtLeast(0.05F)
        val fitScaleY = availableHeight / tiltedHeight.coerceAtLeast(0.05F)
        val fitScale = min(min(fitScaleX, fitScaleZ), fitScaleY) * FIT_PADDING

        return (min(vanillaScale, fitScale) * MODEL_SCALE_MULTIPLIER).coerceAtLeast(0.01F)
    }

    private fun tickSpinState(spinState: SpawnerSpinState, gameTime: Long) {
        if (spinState.lastTick == Long.MIN_VALUE) {
            spinState.lastTick = gameTime - 1
        }

        while (spinState.lastTick < gameTime) {
            spinState.lastTick++
            spinState.oSpin = spinState.spin
            if (spinState.spawnDelay > 0) {
                spinState.spawnDelay--
            }
            spinState.spin = (spinState.spin + 1000.0 / (spinState.spawnDelay.toDouble() + 200.0)) % 360.0
        }
    }
}
