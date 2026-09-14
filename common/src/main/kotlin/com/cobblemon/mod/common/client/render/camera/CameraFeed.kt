/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.camera

import com.mojang.blaze3d.pipeline.RenderTarget
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
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

class CameraFeed(
    val cameraId: UUID,
    initialPosition: Vec3,
    initialResolution: Int,
    initialRenderDistance: Int
) {
    private val sectionsInRange = mutableListOf<RenderSection>()
    private val sectionsInRangePositions = LongOpenHashSet()
    private val visibleSections = mutableListOf<RenderSection>()
    private val compilingSectionsQueue = mutableListOf<RenderSection>()

    private var renderTarget: RenderTarget = RenderTargetManager.getOrCreate(cameraId, initialResolution)
    private var markedForRemoval = false
    private var viewAreaRevision = -1L
    private var graphRevision = -1L
    private var cameraSectionPosition: SectionPos? = null
    private var sectionViewDistance = -1

    var position: Vec3 = initialPosition
        private set
    var resolution: Int = initialResolution.coerceIn(64, 2048)
        private set
    var renderDistance: Int = initialRenderDistance.coerceIn(2, 32)
        private set

    fun updateContext(
        position: Vec3,
        resolution: Int,
        renderDistance: Int
    ) {
        this.position = position

        val sanitizedResolution = resolution.coerceIn(64, 2048)
        if (this.resolution != sanitizedResolution) {
            val replacement = RenderTargetManager.getOrCreate(cameraId, sanitizedResolution)
            renderTarget = replacement
            this.resolution = sanitizedResolution
        }

        this.renderDistance = renderDistance.coerceIn(2, 32)
    }

    fun renderTarget(): RenderTarget = renderTarget

    fun updateVisibleSections(frustum: Frustum) {
        visibleSections.clear()

        for (section in sectionsInRange) {
            if (frustum.isVisible(section.boundingBox)) {
                visibleSections.add(section)
            }
        }
    }

    fun applyVisibleSections(currentVisibleSections: MutableList<RenderSection>) {
        if (viewAreaRevision != CameraViewAreaExtension.revision()) {
            visibleSections.clear()
        }
        currentVisibleSections.clear()
        currentVisibleSections.addAll(visibleSections)
    }

    fun markForRemoval() {
        markedForRemoval = true
        try {
            CameraViewAreaExtension.releaseSections(cameraId)
        } finally {
            sectionsInRangePositions.clear()
            sectionsInRange.clear()
            visibleSections.clear()
            compilingSectionsQueue.clear()
        }
    }

    fun shouldBeRemoved(): Boolean = markedForRemoval

    fun dispose() {
        try {
            markForRemoval()
        } finally {
            RenderTargetManager.remove(cameraId)
        }
    }

    fun refreshViewArea(level: Level, viewDistance: Int) {
        discoverVisibleSections(level, position, viewDistance)
    }

    private fun initialiseSections(level: Level) {
        sectionsInRange.clear()
        sectionsInRangePositions.clear()
        visibleSections.clear()
        compilingSectionsQueue.clear()
        viewAreaRevision = CameraViewAreaExtension.revision()
        graphRevision = CameraViewAreaExtension.graphRevision()
        cameraSectionPosition = SectionPos.of(BlockPos.containing(position))
        sectionViewDistance = renderDistance

        val sectionPos = cameraSectionPosition!!
        val clampedY = sectionPos.y().coerceIn(level.minSection, level.maxSection - 1)
        val startingSection = CameraViewAreaExtension.rawFetch(sectionPos.x(), clampedY, sectionPos.z(), true)
        if (startingSection != null) {
            sectionsInRange.add(startingSection)
            sectionsInRangePositions.add(startingSection.origin.asLong())
            compilingSectionsQueue.add(startingSection)
        }
    }

    private fun discoverVisibleSections(level: Level, cameraPos: Vec3, viewDistance: Int) {
        val cameraSectionPos = SectionPos.of(BlockPos.containing(cameraPos))
        val sanitizedDistance = viewDistance.coerceIn(2, 32)
        if (cameraSectionPosition != cameraSectionPos || sectionViewDistance != sanitizedDistance ||
            viewAreaRevision != CameraViewAreaExtension.revision() ||
            graphRevision != CameraViewAreaExtension.graphRevision()) {
            initialiseSections(level)
            sectionViewDistance = sanitizedDistance
        }
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
                if (!canSeeNeighborFace(compiledSection, direction)) continue
                val cx = SectionPos.blockToSectionCoord(origin.x) + direction.stepX
                val cy = SectionPos.blockToSectionCoord(origin.y) + direction.stepY
                val cz = SectionPos.blockToSectionCoord(origin.z) + direction.stepZ
                if (!ChunkTrackingView.isInViewDistance(cameraSectionPos.x(), cameraSectionPos.z(), sanitizedDistance, cx, cz)) continue
                val neighbourSection = CameraViewAreaExtension.rawFetch(cx, cy, cz, true) ?: continue
                if (sectionsInRangePositions.add(neighbourSection.origin.asLong())) {
                    sectionsInRange.add(neighbourSection)
                    queue.addLast(neighbourSection)
                }
            }
        }
        CameraViewAreaExtension.retainSections(cameraId, sectionsInRange)
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
