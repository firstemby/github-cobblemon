/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.pokemon

import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.client.render.MatrixWrapper
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.toVec3d
import net.minecraft.client.renderer.RenderType
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import java.lang.Math.clamp
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 *
 * Renders an alpha eye trail based on a list of previous eye locator positions
 *
 * @author Jackson
 * @since March 21st, 2026
 */
private const val TARGET_DIMENSION_SIZE_FOR_RENDERING = 3.0f // Without scaling this is what the rendering is designed for.
private const val TRAIL_MAX_WIDTH = 0.1f
private const val BLOOM_RADIUS = 0.35f
private const val TRAIL_MAX_ALPHA = 0.6f
private val TRAIL_COLOR = Vector3f(0.7f, 0.0f, 0.0f)
private const val TRAIL_DURATION_MS = 400 // lookback distance in ms for previous eye positions for trail rendering

/**
 * Updates the eye trail positions based on the current entity position and locator states
 */
fun updateEyeTrail(
    aspects: Set<String>,
    locatorStates: Map<String, MatrixWrapper>,
    entityPos: Vec3,
    trailPositions: MutableMap<String, ArrayDeque<Pair<Vec3, Long>>>
) {
    if ("alpha_eyes" !in aspects) {
        trailPositions.clear()
        return
    }
    for (locatorName in locatorStates.keys.filter { "eye" in it.lowercase() }) {
        val wrapper = locatorStates[locatorName] ?: continue
        val pos = wrapper.matrix.getTranslation(Vector3f())
        val worldPos = Vec3(
            entityPos.x + pos.x,
            entityPos.y + pos.y,
            entityPos.z + pos.z
        )
        val now = System.currentTimeMillis()
        val deque = trailPositions.getOrPut(locatorName) { ArrayDeque() }
        deque.addLast(worldPos to now)
        while (deque.isNotEmpty() && now - deque.first().second > TRAIL_DURATION_MS) {
            deque.removeFirst()
        }
    }
}

/**
 * Renders alpha eye bloom using concentric rings
 */
fun renderAlphaEyeBloom(
    entity: PokemonEntity,
    partialTicks: Float,
    camPos: Vec3,
    poseStack: PoseStack,
    bufferSource: MultiBufferSource
) {
    val clientDelegate = entity.delegate as PokemonClientDelegate
    if ("alpha_eyes" !in clientDelegate.currentAspects) return

    val entityPos = entity.getPosition(partialTicks)
    val locatorStates = clientDelegate.locatorStates

    // Render bloom for all eye locators present on the model
    for (locatorName in locatorStates.keys.filter { "eye" in it.lowercase() }) {
        val wrapper = locatorStates[locatorName] ?: continue

        // Gather the forward vector for this eye (useful for positioning the bloom and determining if we are head on with the eye)
        val eyeForward = wrapper.matrix.transformDirection(Vector3f(0f, 0f, -1f)).toVec3d()

        // Gather eye position and camera position vectors for the bloom calculation
        val matrix = poseStack.last().pose()
        val eyeLocalPos = wrapper.matrix.getTranslation(Vector3f()).toVec3d()
        val eyeWorldPos = entityPos.add(eyeLocalPos)
        val toCam = camPos.subtract(eyeWorldPos).normalize()
        val camUp = Minecraft.getInstance().gameRenderer.mainCamera.upVector.toVec3d()
        val camPerp = camUp.cross(toCam).normalize()

        // Have the bloom shrink depending upon if you're looking at the eye from the side or behind.
        // This article talks about how GTA5 did this for their bloom as well: https://simonschreibt.de/gat/gta-v-underestimated-glow/
        //val radius = 0.35 * clamp((eyeForward.dot(toCam) + 1.0) * 0.5, 0.0, 1.0)
        val offAngleModQuad = clamp(eyeForward.dot(toCam) + 0.5, 0.0, 1.0).pow(2)
        val offAngleModLinear = clamp(eyeForward.dot(toCam) + 0.5, 0.0, 1.0)
        val radius = BLOOM_RADIUS * offAngleModQuad * getHitboxScaling(entity)
        val bloomEyeDist = 0.1 * offAngleModLinear // Also move the bloom closer to the eye if you're looking at it from the side

        // Set bloom values
        val bloomCenter = eyeLocalPos.add(toCam.scale(bloomEyeDist)) // Move forward off the eyes a bit
        val centerAlpha = 1.0f
        val edgeAlpha = 0.0f
        val segments = 12 // Number of triangles in our circle ring pizza of a bloom
        val angleStep = (2.0 * Math.PI) / segments
        val r = TRAIL_COLOR.x
        val g = TRAIL_COLOR.y
        val b = TRAIL_COLOR.z

        // Render bloom using concentric "rings" (its all just triangle pizza in the end)
        val rings = 5 //TODO: is this too many?
        val consumer = bufferSource.getBuffer(RenderType.dragonRays()) // rgba flat color triangle rendering

        for (ring in 0 until rings) {
            val t0 = ring.toFloat() / rings
            val t1 = (ring + 1).toFloat() / rings

            var r0 = radius * t0
            var r1 = radius * t1

            // Quadratic falloff as the rings get farther from the center
            val a0 = centerAlpha * sqrt(1.0f - t0)
            val a1 = centerAlpha * sqrt(1.0f - t1)

            for (i in 0 until segments) {
                val ang0 = angleStep * i
                val ang1 = angleStep * (i + 1)

                // Just grab some noise using this method found here: https://thebookofshaders.com/11/ (I say found here but I think this is pretty standard)
                val noise = 0.95f + 0.05f * sin(System.currentTimeMillis() * 0.003 + (ang0 * 2.0 * 432151)).toFloat()

                // TODO: Reinclude when I can figure out how to not make it shite
                val a0 = centerAlpha * (1.0f - t0).pow(2) //* noise
                val a1 = centerAlpha * (1.0f - t1).pow(2) //* noise
//
//                r0 *= noise
//                r1 *= noise

                val dir0 = camPerp.scale(cos(ang0)).add(camUp.scale(sin(ang0)))
                val dir1 = camPerp.scale(cos(ang1)).add(camUp.scale(sin(ang1)))

                val p00 = bloomCenter.add(dir0.scale(r0))
                val p01 = bloomCenter.add(dir1.scale(r0))
                val p10 = bloomCenter.add(dir0.scale(r1))
                val p11 = bloomCenter.add(dir1.scale(r1))

                // Two triangles per quad: p00, p10, p11 and p00, p11, p01
                consumer.addVertex(matrix, p00.x.toFloat(), p00.y.toFloat(), p00.z.toFloat())
                    .setColor(r, g, b, a0)
                consumer.addVertex(matrix, p10.x.toFloat(), p10.y.toFloat(), p10.z.toFloat())
                    .setColor(r, g, b, a1)
                consumer.addVertex(matrix, p11.x.toFloat(), p11.y.toFloat(), p11.z.toFloat())
                    .setColor(r, g, b, a1)

                consumer.addVertex(matrix, p00.x.toFloat(), p00.y.toFloat(), p00.z.toFloat())
                    .setColor(r, g, b, a0)
                consumer.addVertex(matrix, p11.x.toFloat(), p11.y.toFloat(), p11.z.toFloat())
                    .setColor(r, g, b, a1)
                consumer.addVertex(matrix, p01.x.toFloat(), p01.y.toFloat(), p01.z.toFloat())
                    .setColor(r, g, b, a0)
            }
        }
    }
}

fun renderEyeTrail(
    positions: ArrayDeque<Pair<Vec3, Long>>,
    partialTicks: Float,
    entity: PokemonEntity,
    camPos: Vec3,
    poseStack: PoseStack,
    bufferSource: MultiBufferSource
) {
    if (positions.size < 2) return

    val consumer = bufferSource.getBuffer(RenderType.lightning())
    val entityPos = entity.getPosition(partialTicks)
    val matrix = poseStack.last().pose()
    val n = positions.size
    val targetSize = TRAIL_MAX_WIDTH * getHitboxScaling(entity)


    for (i in 0 until n - 1) {
        val curr = positions[i].first
        val next = positions[i + 1].first

        val tCurr = i.toFloat() / (n - 1)
        val tNext = (i + 1).toFloat() / (n - 1)

        val wCurr = targetSize * tCurr
        val wNext = targetSize * tNext
        val aCurr = TRAIL_MAX_ALPHA * tCurr
        val aNext = TRAIL_MAX_ALPHA * tNext

        val localCurr = curr.subtract(entityPos)
        val localNext = next.subtract(entityPos)

        val dir = next.subtract(curr).normalize()
        val toCamera = camPos.subtract(curr).normalize()
        val perp = dir.cross(toCamera).normalize()

        val px = perp.x.toFloat()
        val py = perp.y.toFloat()
        val pz = perp.z.toFloat()

        val r = TRAIL_COLOR.x
        val g = TRAIL_COLOR.y
        val b = TRAIL_COLOR.z

        consumer.addVertex(matrix,
            (localCurr.x - px * wCurr).toFloat(),
            (localCurr.y - py * wCurr).toFloat(),
            (localCurr.z - pz * wCurr).toFloat()
        ).setColor(r, g, b, aCurr)

        consumer.addVertex(matrix,
            (localCurr.x + px * wCurr).toFloat(),
            (localCurr.y + py * wCurr).toFloat(),
            (localCurr.z + pz * wCurr).toFloat()
        ).setColor(r, g, b, aCurr)

        consumer.addVertex(matrix,
            (localNext.x + px * wNext).toFloat(),
            (localNext.y + py * wNext).toFloat(),
            (localNext.z + pz * wNext).toFloat()
        ).setColor(r, g, b, aNext)

        consumer.addVertex(matrix,
            (localNext.x - px * wNext).toFloat(),
            (localNext.y - py * wNext).toFloat(),
            (localNext.z - pz * wNext).toFloat()
        ).setColor(r, g, b, aNext)
    }
}


fun doAlphaEyeRendering(
    entity: PokemonEntity,
    partialTicks: Float,
    poseStack: PoseStack,
    bufferSource: MultiBufferSource
) {

    val clientDelegate = entity.delegate as PokemonClientDelegate
    val camPos = Minecraft.getInstance().gameRenderer.mainCamera.position

    updateEyeTrail(
        aspects = clientDelegate.currentAspects,
        locatorStates = clientDelegate.locatorStates,
        entityPos = entity.getPosition(partialTicks),
        trailPositions = clientDelegate.eyeTrailPositions
    )
    renderAlphaEyeBloom(
        entity = entity,
        partialTicks = partialTicks,
        camPos = camPos,
        poseStack = poseStack,
        bufferSource = bufferSource
    )
    if (clientDelegate.eyeTrailPositions.isNotEmpty()) {
        for ((_, positions) in clientDelegate.eyeTrailPositions) {
            renderEyeTrail(
                positions = positions,
                partialTicks = partialTicks,
                entity = entity,
                camPos = camPos,
                poseStack = poseStack,
                bufferSource = bufferSource
            )
        }
    }
}

/**
 * Calculates the scaling applied to the eye rendering based on the entities
 * largest hitbox dimension relative to a TARGET_DIMENSION_SIZE_FOR_RENDERING.
 */
fun getHitboxScaling(
    entity: PokemonEntity
): Float {
    val form = entity.pokemon.form
    val hitbox = form.hitbox
    val largestHitboxDimension = max(hitbox.width, hitbox.height) * form.baseScale * entity.pokemon.effectiveScale
    val percHit = largestHitboxDimension / TARGET_DIMENSION_SIZE_FOR_RENDERING
    return sqrt(percHit) // Take the square root of the percentage increase or decrease from the target dimension. This is to make the change not so drastic
}
