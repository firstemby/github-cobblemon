/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.camera.debug

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.MoValue
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.camera.CameraManager
import com.cobblemon.mod.common.client.camera.*
import com.cobblemon.mod.common.client.camera.tracks.*
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.platform.events.RenderEvent
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f

/**
 * Renders debug visualization for camera tracks.
 * Shows the camera path, keyframes, and focus targets for the active timeline.
 *
 * @author Terrainwax
 * @since January 2025
 */
object CameraTrackDebugRenderer {

    @Volatile
    private var enabled = false
    private var initialized = false

    @Volatile
    private var debugTimelineId: String? = null

    @Volatile
    private var debugOrigin: Vec3 = Vec3.ZERO

    private var hasLoggedDebugInfo = false

    // Hover detection
    var hoveredKeyframe: HoveredKeyframe? = null

    data class HoveredKeyframe(
        val stateId: String,
        val trackIndex: Int,
        val keyframeIndex: Int,
        val position: Vec3,
        val time: Float,
        val info: String
    )

    // Debug settings
    private const val PATH_SEGMENTS_PER_SECOND = 10 // How many line segments to draw per second of animation
    private const val KEYFRAME_MARKER_SIZE = 0.2f
    private const val FOCUS_MARKER_SIZE = 0.15f
    private const val HOVER_DISTANCE = 10.0 // Max distance to detect hovered keyframes

    fun init() {
        if (initialized) return
        initialized = true

        PlatformEvents.RENDER.subscribe(Priority.NORMAL) { event ->
            if (!enabled || event.stage != RenderEvent.Stage.TRANSLUCENT) {
                return@subscribe
            }
            render(event)
        }
    }

    fun toggle(): Boolean {
        val newValue = !enabled
        setEnabled(newValue)
        return newValue
    }

    fun isEnabled(): Boolean = enabled

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    /**
     * Sets which timeline to visualize for debugging.
     * Call this when starting a camera test to automatically visualize it.
     */
    fun setDebugTimeline(timelineId: String?, origin: Vec3 = Vec3.ZERO) {
        debugTimelineId = timelineId
        debugOrigin = origin
        hasLoggedDebugInfo = false // Reset logging flag when timeline changes
    }

    private fun render(event: RenderEvent) {
        val timelineId = debugTimelineId ?: return
        renderCameraTimeline(event, timelineId, debugOrigin)
    }

    private fun renderCameraTimeline(event: RenderEvent, timelineId: String, origin: Vec3) {
        val timeline = CameraTimelineRegistry.getTimeline(timelineId)
        if (timeline == null) {
            if (!hasLoggedDebugInfo) {
                Minecraft.getInstance().player?.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("[CameraDebug] Timeline '$timelineId' not found in registry")
                )
                hasLoggedDebugInfo = true
            }
            return
        }

        if (!hasLoggedDebugInfo) {
            Minecraft.getInstance().player?.sendSystemMessage(
                net.minecraft.network.chat.Component.literal("[CameraDebug] Timeline: $timelineId, States: ${timeline.states.size}, Origin: $origin")
            )
        }

        val cameraPosition = event.camera.position
        val camera = event.camera

        if (!hasLoggedDebugInfo) {
            Minecraft.getInstance().player?.sendSystemMessage(
                net.minecraft.network.chat.Component.literal("[CameraDebug] Camera pos: ${cameraPosition.x.toInt()}, ${cameraPosition.y.toInt()}, ${cameraPosition.z.toInt()}")
            )
        }

        // Update hover detection
        updateHoverDetection(timeline, origin, camera)

        val poseStack = event.poseStack

        poseStack.pushPose()
        poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z)
        val matrix = poseStack.last().pose()

        RenderSystem.enableDepthTest()
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShader { GameRenderer.getPositionColorShader() }
        RenderSystem.lineWidth(2.0f)

        val tessellator = Tesselator.getInstance()
        val buffer = tessellator.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR)

        var hasVertices = false

        // Render all states with different colors
        timeline.states.values.forEachIndexed { stateIndex, state ->
            // Color based on state index for visual distinction
            val hue = (stateIndex.toFloat() / timeline.states.size) * 360f
            val stateColor = hueToRgb(hue)

            if (!hasLoggedDebugInfo) {
                Minecraft.getInstance().player?.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("[CameraDebug] State: ${state.stateId}, Tracks: ${state.tracks.size}")
                )
            }

            // Render each track in this state
            state.tracks.forEachIndexed { trackIndex, track ->
                when (track) {
                    is PositionTrack -> {
                        if (track.keyframes.isNotEmpty()) {
                            renderPositionTrack(buffer, matrix, track, origin, state.stateId, trackIndex, stateColor)
                            hasVertices = true
                        }
                    }
                    is FocusTrack -> {
                        if (track.keyframes.isNotEmpty()) {
                            renderFocusTrack(buffer, matrix, track, origin, state.stateId, trackIndex)
                            hasVertices = true
                        }
                    }
                    is TrackReferenceTrack -> {
                        // Resolve the referenced track and render it
                        val referencedTrack = CameraTrackDefinitionRegistry.getTrack(track.trackId)
                        if (referencedTrack != null) {
                            when (referencedTrack) {
                                is PositionTrack -> {
                                    if (referencedTrack.keyframes.isNotEmpty()) {
                                        renderPositionTrack(buffer, matrix, referencedTrack, origin, state.stateId, trackIndex, stateColor)
                                        hasVertices = true
                                    }
                                }
                                is FocusTrack -> {
                                    if (referencedTrack.keyframes.isNotEmpty()) {
                                        renderFocusTrack(buffer, matrix, referencedTrack, origin, state.stateId, trackIndex)
                                        hasVertices = true
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Only build and draw if we actually added vertices
        if (hasVertices) {
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(buffer.buildOrThrow())
        }

        poseStack.popPose()

        RenderSystem.disableBlend()
        RenderSystem.lineWidth(1.0f)

        // Render hover information as text overlay
        renderHoverInfo(event)

        // Mark debug info as logged to prevent spam
        hasLoggedDebugInfo = true
    }

    private fun renderPositionTrack(
        buffer: BufferBuilder,
        matrix: Matrix4f,
        track: PositionTrack,
        origin: Vec3,
        stateId: String,
        trackIndex: Int,
        stateColor: Triple<Int, Int, Int>
    ) {
        if (track.keyframes.isEmpty()) return

        // Calculate total duration
        val maxTime = track.keyframes.maxOf { it.time }
        val segments = (maxTime * PATH_SEGMENTS_PER_SECOND).toInt().coerceAtLeast(10)

        if (!hasLoggedDebugInfo) {
            Minecraft.getInstance().player?.sendSystemMessage(
                net.minecraft.network.chat.Component.literal("[CameraDebug] PositionTrack: maxTime=$maxTime, segments=$segments, keyframes=${track.keyframes.size}")
            )
        }

        // Render path as connected line segments
        // Set up MoLang context with origin coordinates (required for q.origin_x/y/z queries)
        val context = mutableMapOf<String, MoValue>()
        context["origin_x"] = DoubleValue(origin.x)
        context["origin_y"] = DoubleValue(origin.y)
        context["origin_z"] = DoubleValue(origin.z)
        val runtime = MoLangRuntime()

        var lastPos: Vec3? = null
        var linesDrawn = 0
        var firstPos: Vec3? = null
        var lastRenderedPos: Vec3? = null

        for (i in 0..segments) {
            val time = (i.toFloat() / segments) * maxTime
            val pos = track.evaluate(time, context, runtime, origin)

            if (pos != null) {
                if (firstPos == null) firstPos = pos
                lastRenderedPos = pos
            }

            if (pos != null && lastPos != null) {
                // Draw line segment using state color
                addLine(buffer, matrix, lastPos, pos, stateColor.first, stateColor.second, stateColor.third, 200)
                linesDrawn++
            }
            lastPos = pos
        }

        if (!hasLoggedDebugInfo) {
            Minecraft.getInstance().player?.sendSystemMessage(
                net.minecraft.network.chat.Component.literal("[CameraDebug] Drew $linesDrawn line segments")
            )
            if (firstPos != null && lastRenderedPos != null) {
                Minecraft.getInstance().player?.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("[CameraDebug] Path start: ${firstPos.x.toInt()}, ${firstPos.y.toInt()}, ${firstPos.z.toInt()}")
                )
                Minecraft.getInstance().player?.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("[CameraDebug] Path end: ${lastRenderedPos.x.toInt()}, ${lastRenderedPos.y.toInt()}, ${lastRenderedPos.z.toInt()}")
                )
            }
        }

        // Render keyframe markers
        track.keyframes.forEachIndexed { index, keyframe ->
            val pos = keyframe.position.toVec3(context, origin, track.isRelative, runtime)

            // Check if this keyframe is being hovered
            val isHovered = hoveredKeyframe?.let {
                it.stateId == stateId && it.trackIndex == trackIndex && it.keyframeIndex == index
            } ?: false

            if (isHovered) {
                // Larger white marker for hovered keyframe
                renderMarker(buffer, matrix, pos, KEYFRAME_MARKER_SIZE * 1.5f, 255, 255, 255, 255)
            } else {
                // Yellow marker for normal keyframes
                renderMarker(buffer, matrix, pos, KEYFRAME_MARKER_SIZE, 255, 255, 0, 255)
            }
        }
    }

    private fun renderFocusTrack(
        buffer: BufferBuilder,
        matrix: Matrix4f,
        track: FocusTrack,
        origin: Vec3,
        stateId: String,
        trackIndex: Int
    ) {
        if (track.keyframes.isEmpty()) return

        // Set up MoLang context with origin coordinates (required for q.origin_x/y/z queries)
        val context = mutableMapOf<String, MoValue>()
        context["origin_x"] = DoubleValue(origin.x)
        context["origin_y"] = DoubleValue(origin.y)
        context["origin_z"] = DoubleValue(origin.z)
        val runtime = MoLangRuntime()

        // Render focus targets
        for (keyframe in track.keyframes) {
            if (keyframe.target is LookTarget.Point) {
                val pos = keyframe.target.position.toVec3(context, origin, false, runtime)
                // Draw focus marker - magenta
                renderMarker(buffer, matrix, pos, FOCUS_MARKER_SIZE, 255, 0, 255, 255)
            }
        }
    }

    private fun renderMarker(
        buffer: BufferBuilder,
        matrix: Matrix4f,
        center: Vec3,
        size: Float,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int
    ) {
        val x = center.x.toFloat()
        val y = center.y.toFloat()
        val z = center.z.toFloat()

        // Draw a 3D cross marker
        addLine(buffer, matrix,
            Vec3((x - size).toDouble(), y.toDouble(), z.toDouble()),
            Vec3((x + size).toDouble(), y.toDouble(), z.toDouble()),
            red, green, blue, alpha)
        addLine(buffer, matrix,
            Vec3(x.toDouble(), (y - size).toDouble(), z.toDouble()),
            Vec3(x.toDouble(), (y + size).toDouble(), z.toDouble()),
            red, green, blue, alpha)
        addLine(buffer, matrix,
            Vec3(x.toDouble(), y.toDouble(), (z - size).toDouble()),
            Vec3(x.toDouble(), y.toDouble(), (z + size).toDouble()),
            red, green, blue, alpha)
    }

    private fun addLine(
        buffer: BufferBuilder,
        matrix: Matrix4f,
        start: Vec3,
        end: Vec3,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int
    ) {
        buffer.addVertex(matrix, start.x.toFloat(), start.y.toFloat(), start.z.toFloat())
            .setColor(red, green, blue, alpha)
        buffer.addVertex(matrix, end.x.toFloat(), end.y.toFloat(), end.z.toFloat())
            .setColor(red, green, blue, alpha)
    }

    /**
     * Updates hover detection by raycasting from the camera to find intersecting keyframes.
     */
    private fun updateHoverDetection(timeline: CameraTimeline, origin: Vec3, camera: net.minecraft.client.Camera) {
        val rayOrigin = camera.position
        val lookVec = camera.lookVector
        val rayEnd = rayOrigin.add(lookVec.x * HOVER_DISTANCE, lookVec.y * HOVER_DISTANCE, lookVec.z * HOVER_DISTANCE)

        val context = mutableMapOf<String, MoValue>()
        context["origin_x"] = DoubleValue(origin.x)
        context["origin_y"] = DoubleValue(origin.y)
        context["origin_z"] = DoubleValue(origin.z)
        val runtime = MoLangRuntime()

        var closestDist = HOVER_DISTANCE
        var closestHover: HoveredKeyframe? = null

        timeline.states.values.forEach { state ->
            state.tracks.forEachIndexed { trackIndex, track ->
                when (track) {
                    is PositionTrack -> {
                        track.keyframes.forEachIndexed { keyframeIndex, keyframe ->
                            val pos = keyframe.position.toVec3(context, origin, track.isRelative, runtime)
                            val aabb = net.minecraft.world.phys.AABB(
                                pos.x - KEYFRAME_MARKER_SIZE / 2, pos.y - KEYFRAME_MARKER_SIZE / 2, pos.z - KEYFRAME_MARKER_SIZE / 2,
                                pos.x + KEYFRAME_MARKER_SIZE / 2, pos.y + KEYFRAME_MARKER_SIZE / 2, pos.z + KEYFRAME_MARKER_SIZE / 2
                            )
                            val clip = aabb.clip(rayOrigin, rayEnd)
                            if (clip.isPresent) {
                                val dist = rayOrigin.distanceTo(clip.get())
                                if (dist < closestDist) {
                                    closestDist = dist
                                    val info = buildKeyframeInfo(keyframe, track, state.stateId)
                                    closestHover = HoveredKeyframe(state.stateId, trackIndex, keyframeIndex, pos, keyframe.time, info)
                                }
                            }
                        }
                    }
                    is FocusTrack -> {
                        track.keyframes.forEachIndexed { keyframeIndex, keyframe ->
                            if (keyframe.target is LookTarget.Point) {
                                val pos = keyframe.target.position.toVec3(context, origin, false, runtime)
                                val aabb = net.minecraft.world.phys.AABB(
                                    pos.x - FOCUS_MARKER_SIZE / 2, pos.y - FOCUS_MARKER_SIZE / 2, pos.z - FOCUS_MARKER_SIZE / 2,
                                    pos.x + FOCUS_MARKER_SIZE / 2, pos.y + FOCUS_MARKER_SIZE / 2, pos.z + FOCUS_MARKER_SIZE / 2
                                )
                                val clip = aabb.clip(rayOrigin, rayEnd)
                                if (clip.isPresent) {
                                    val dist = rayOrigin.distanceTo(clip.get())
                                    if (dist < closestDist) {
                                        closestDist = dist
                                        val info = buildFocusKeyframeInfo(keyframe, state.stateId)
                                        closestHover = HoveredKeyframe(state.stateId, trackIndex, keyframeIndex, pos, keyframe.time, info)
                                    }
                                }
                            }
                        }
                    }
                    is TrackReferenceTrack -> {
                        val referencedTrack = CameraTrackDefinitionRegistry.getTrack(track.trackId)
                        if (referencedTrack is PositionTrack) {
                            referencedTrack.keyframes.forEachIndexed { keyframeIndex, keyframe ->
                                val pos = keyframe.position.toVec3(context, origin, referencedTrack.isRelative, runtime)
                                val aabb = net.minecraft.world.phys.AABB(
                                    pos.x - KEYFRAME_MARKER_SIZE / 2, pos.y - KEYFRAME_MARKER_SIZE / 2, pos.z - KEYFRAME_MARKER_SIZE / 2,
                                    pos.x + KEYFRAME_MARKER_SIZE / 2, pos.y + KEYFRAME_MARKER_SIZE / 2, pos.z + KEYFRAME_MARKER_SIZE / 2
                                )
                                val clip = aabb.clip(rayOrigin, rayEnd)
                                if (clip.isPresent) {
                                    val dist = rayOrigin.distanceTo(clip.get())
                                    if (dist < closestDist) {
                                        closestDist = dist
                                        val info = buildKeyframeInfo(keyframe, referencedTrack, state.stateId)
                                        closestHover = HoveredKeyframe(state.stateId, trackIndex, keyframeIndex, pos, keyframe.time, info)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        hoveredKeyframe = closestHover
    }

    private fun buildKeyframeInfo(keyframe: PositionKeyframe, track: PositionTrack, stateId: String): String {
        return """
            State: $stateId
            Time: ${keyframe.time}s
            Easing: ${keyframe.easing ?: track.easing}
            Interpolation: ${track.interpolationType}
            Relative: ${track.isRelative}
        """.trimIndent()
    }

    private fun buildFocusKeyframeInfo(keyframe: FocusKeyframe, stateId: String): String {
        return """
            State: $stateId
            Time: ${keyframe.time}s
            Type: Focus Target
            Easing: ${keyframe.easing ?: "default"}
        """.trimIndent()
    }

    /**
     * Renders hover information as a tooltip-style window in 3D world space above the keyframe.
     */
    private fun renderHoverInfo(event: RenderEvent) {
        val hovered = hoveredKeyframe ?: return
        val minecraft = Minecraft.getInstance()
        val font = minecraft.font
        val poseStack = event.poseStack
        val camera = event.camera
        val cameraPos = camera.position

        val lines = hovered.info.split("\n")

        poseStack.pushPose()

        // Translate to world position above the keyframe
        val textPos = hovered.position.add(0.0, 0.5, 0.0) // 0.5 blocks above keyframe
        poseStack.translate(
            textPos.x - cameraPos.x,
            textPos.y - cameraPos.y,
            textPos.z - cameraPos.z
        )

        // Billboard effect - rotate to face camera
        poseStack.mulPose(org.joml.Quaternionf().rotationYXZ(
            -camera.yRot * (Math.PI.toFloat() / 180f),
            camera.xRot * (Math.PI.toFloat() / 180f),
            0f
        ))

        // Scale text appropriately
        val scale = 0.025f
        poseStack.scale(-scale, -scale, scale)

        val bufferSource = minecraft.renderBuffers().bufferSource()

        // Calculate tooltip dimensions
        val maxWidth = lines.maxOfOrNull { font.width(it) } ?: 0
        val padding = 4
        val lineHeight = 10
        val tooltipWidth = maxWidth + padding * 2
        val tooltipHeight = lines.size * lineHeight + padding * 2

        // Center the tooltip
        val xOffset = -tooltipWidth / 2f
        val yOffset = 0f

        val matrix = poseStack.last().pose()

        // Draw tooltip-style background with borders
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShader { GameRenderer.getPositionColorShader() }

        val tessellator = Tesselator.getInstance()
        val buffer = tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)

        // Colors matching Minecraft's tooltip style
        val bgColor = 0xF0100010.toInt() // Dark purple-ish background
        val borderColorStart = 0x505000FF.toInt() // Purple border gradient start
        val borderColorEnd = 0x5028007F.toInt() // Purple border gradient end

        // Main background
        fillGradient(buffer, matrix,
            xOffset + 1, yOffset + 1,
            xOffset + tooltipWidth - 1, yOffset + tooltipHeight - 1,
            bgColor, bgColor)

        // Top border gradient
        fillGradient(buffer, matrix,
            xOffset, yOffset,
            xOffset + tooltipWidth, yOffset + 1,
            borderColorStart, borderColorStart)

        // Bottom border gradient
        fillGradient(buffer, matrix,
            xOffset, yOffset + tooltipHeight - 1,
            xOffset + tooltipWidth, yOffset + tooltipHeight,
            borderColorEnd, borderColorEnd)

        // Left border
        fillGradient(buffer, matrix,
            xOffset, yOffset + 1,
            xOffset + 1, yOffset + tooltipHeight - 1,
            borderColorStart, borderColorEnd)

        // Right border
        fillGradient(buffer, matrix,
            xOffset + tooltipWidth - 1, yOffset + 1,
            xOffset + tooltipWidth, yOffset + tooltipHeight - 1,
            borderColorStart, borderColorEnd)

        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(buffer.buildOrThrow())

        // Render text lines
        lines.forEachIndexed { index, line ->
            val textX = xOffset + padding
            val textY = yOffset + padding + index * lineHeight

            font.drawInBatch(
                line,
                textX,
                textY,
                0xFFFFFFFF.toInt(),
                false,
                poseStack.last().pose(),
                bufferSource,
                net.minecraft.client.gui.Font.DisplayMode.NORMAL,
                0,
                15728880 // Full brightness
            )
        }

        bufferSource.endBatch()
        poseStack.popPose()
    }

    /**
     * Helper to fill a gradient rectangle (for tooltip rendering).
     */
    private fun fillGradient(
        buffer: BufferBuilder,
        matrix: Matrix4f,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        color1: Int,
        color2: Int
    ) {
        val a1 = (color1 shr 24 and 255)
        val r1 = (color1 shr 16 and 255)
        val g1 = (color1 shr 8 and 255)
        val b1 = (color1 and 255)

        val a2 = (color2 shr 24 and 255)
        val r2 = (color2 shr 16 and 255)
        val g2 = (color2 shr 8 and 255)
        val b2 = (color2 and 255)

        buffer.addVertex(matrix, x2, y1, 0f).setColor(r1, g1, b1, a1)
        buffer.addVertex(matrix, x1, y1, 0f).setColor(r1, g1, b1, a1)
        buffer.addVertex(matrix, x1, y2, 0f).setColor(r2, g2, b2, a2)
        buffer.addVertex(matrix, x2, y2, 0f).setColor(r2, g2, b2, a2)
    }

    /**
     * Converts HSV hue (0-360) to RGB triple (0-255).
     */
    private fun hueToRgb(hue: Float): Triple<Int, Int, Int> {
        val h = hue / 60f
        val c = 255
        val x = (c * (1 - kotlin.math.abs((h % 2) - 1))).toInt()

        return when (h.toInt()) {
            0 -> Triple(c, x, 0)
            1 -> Triple(x, c, 0)
            2 -> Triple(0, c, x)
            3 -> Triple(0, x, c)
            4 -> Triple(x, 0, c)
            else -> Triple(c, 0, x)
        }
    }
}
