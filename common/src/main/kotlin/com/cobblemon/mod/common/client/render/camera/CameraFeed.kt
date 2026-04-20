/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.camera

import com.google.common.util.concurrent.AtomicDouble
import com.mojang.blaze3d.pipeline.RenderTarget
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.CompiledSection
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.SectionPos
import net.minecraft.server.level.ChunkTrackingView
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.ArrayDeque
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

class CameraFeed(
    val cameraId: UUID,
    initialPosition: Vec3,
    initialYaw: Float,
    initialPitch: Float,
    initialRoll: Float,
    initialFov: Float,
    initialResolution: Int,
    initialRenderDistance: Int,
    level: Level
) {
    private val lastActiveTime = AtomicDouble(0.0)
    private val sectionsInRange = mutableListOf<RenderSection>()
    private val sectionsInRangePositions = LongOpenHashSet()
    private val visibleSections = mutableListOf<RenderSection>()
    private val compilingSectionsQueue = mutableListOf<RenderSection>()

    private var renderTarget: RenderTarget = RenderTargetManager.getOrCreate(cameraId, initialResolution)
    private var requiresFrustumUpdate = true
    private var markedForRemoval = false

    var position: Vec3 = initialPosition
        private set
    var yaw: Float = initialYaw
        private set
    var pitch: Float = initialPitch
        private set
    var roll: Float = initialRoll
        private set
    var fov: Float = initialFov
        private set
    var resolution: Int = initialResolution.coerceIn(64, 2048)
        private set
    var renderDistance: Int = initialRenderDistance.coerceIn(2, 32)
        private set

    init {
        initialiseSections(level)
    }

    fun updateContext(
        level: Level,
        position: Vec3,
        yaw: Float,
        pitch: Float,
        roll: Float,
        fov: Float,
        resolution: Int,
        renderDistance: Int
    ) {
        this.position = position
        this.yaw = yaw
        this.pitch = pitch
        this.roll = roll
        this.fov = fov

        val sanitizedResolution = resolution.coerceIn(64, 2048)
        if (this.resolution != sanitizedResolution) {
            this.resolution = sanitizedResolution
            renderTarget = RenderTargetManager.getOrCreate(cameraId, sanitizedResolution)
        }

        val sanitizedDistance = renderDistance.coerceIn(2, 32)
        if (this.renderDistance != sanitizedDistance) {
            this.renderDistance = sanitizedDistance
            requireFrustumUpdate()
        }
    }

    fun renderTarget(): RenderTarget = renderTarget

    fun textureId(): Int? = RenderTargetManager.getTextureId(cameraId)

    fun lastActiveTime(): Double = lastActiveTime.get()

    fun markActive(time: Double) {
        lastActiveTime.set(time)
    }

    fun requireFrustumUpdate() {
        requiresFrustumUpdate = true
    }

    fun requiresFrustumUpdate(): Boolean = requiresFrustumUpdate

    fun updateVisibleSections(frustum: Frustum) {
        requiresFrustumUpdate = false
        visibleSections.clear()

        for (section in sectionsInRange) {
            if (frustum.isVisible(section.boundingBox)) {
                visibleSections.add(section)
            }
        }
    }

    fun applyVisibleSections(currentVisibleSections: MutableList<RenderSection>) {
        currentVisibleSections.clear()
        currentVisibleSections.addAll(visibleSections)
    }

    fun hasVisibleSections(): Boolean = visibleSections.isNotEmpty()

    fun isSectionTracked(sectionPos: SectionPos): Boolean =
        sectionsInRangePositions.contains(sectionPos.asLong())

    fun markForRemoval() {
        markedForRemoval = true
        sectionsInRangePositions.clear()
        sectionsInRange.clear()
        visibleSections.clear()
        compilingSectionsQueue.clear()
    }

    fun shouldBeRemoved(): Boolean = markedForRemoval

    fun dispose() {
        RenderTargetManager.remove(cameraId)
    }

    fun refreshViewArea(level: Level, viewDistance: Int) {
        discoverVisibleSections(level, position, viewDistance)
    }

    private fun initialiseSections(level: Level) {
        sectionsInRange.clear()
        sectionsInRangePositions.clear()
        visibleSections.clear()
        compilingSectionsQueue.clear()

        val sectionPos = SectionPos.of(BlockPos.containing(position))
        val minSectionY = CameraViewAreaExtension.minSectionY()
        val maxSectionY = CameraViewAreaExtension.maxSectionY()
        val clampedY = sectionPos.y().coerceIn(minSectionY, maxSectionY - 1)
        val startingSection = CameraViewAreaExtension.rawFetch(sectionPos.x(), clampedY, sectionPos.z(), true)

        if (startingSection != null) {
            sectionsInRange.add(startingSection)
            sectionsInRangePositions.add(startingSection.origin.asLong())
            compilingSectionsQueue.add(startingSection)
        }

        discoverVisibleSections(level, position, renderDistance)
    }

    fun discoverVisibleSections(level: Level, cameraPos: Vec3, viewDistance: Int) {
        val cameraSectionPos = SectionPos.of(BlockPos.containing(cameraPos))
        val queue = ArrayDeque(compilingSectionsQueue)

        compilingSectionsQueue.clear()

        while (queue.isNotEmpty()) {
            val currentSection = queue.removeFirst()
            val origin = currentSection.origin
            val compiledSection = currentSection.compiled.get()

            if (compiledSection == CompiledSection.UNCOMPILED) {
                compilingSectionsQueue.add(currentSection)
                continue
            }

            for (direction in Direction.entries) {
                val cx = SectionPos.blockToSectionCoord(origin.x) + direction.stepX
                val cy = SectionPos.blockToSectionCoord(origin.y) + direction.stepY
                val cz = SectionPos.blockToSectionCoord(origin.z) + direction.stepZ

                if (ChunkTrackingView.isInViewDistance(cameraSectionPos.x(), cameraSectionPos.z(), viewDistance, cx, cz)) {
                    val neighbourSection = CameraViewAreaExtension.rawFetch(cx, cy, cz, true)

                    if (neighbourSection != null) {
                        val neighbourPosAsLong = neighbourSection.getOrigin().asLong()

                        if (!sectionsInRangePositions.contains(neighbourPosAsLong) && canSeeNeighborFace(compiledSection, direction)) {
                            sectionsInRange.add(neighbourSection)
                            sectionsInRangePositions.add(neighbourSection.getOrigin().asLong())
                            compilingSectionsQueue.add(neighbourSection)
                            requireFrustumUpdate()
                        }

                    }

                }
            }
        }
    }

    private fun canSeeNeighborFace(compiled: CompiledSection, direction: Direction): Boolean {
        for (other in Direction.entries) {
            if (compiled.facesCanSeeEachother(other.opposite, direction)) {
                return true
            }
        }
        return false
    }
}

