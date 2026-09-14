/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.cinematic

import net.minecraft.world.phys.Vec3
import com.cobblemon.mod.common.client.editor.cinematic.EditorTheme
import com.cobblemon.mod.common.client.editor.cinematic.SceneEditorOverlay
import com.cobblemon.mod.common.client.imgui.CobblemonImGui
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.platform.events.RenderEvent
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderType
import org.joml.Vector3f
import kotlin.math.abs

internal fun frustumBasis(forwardHint: Vector3f, upHint: Vector3f): Pair<Vector3f, Vector3f>? {
    val forward = Vector3f(forwardHint)
    if (!forward.isFiniteVector() || forward.lengthSquared() <= 1e-8f) return null
    forward.normalize()
    val up = Vector3f(upHint).sub(Vector3f(forward).mul(upHint.dot(forward)))
    if (up.lengthSquared() <= 1e-8f) {
        val fallback = if (abs(forward.y) < 0.9f) Vector3f(0f, 1f, 0f) else Vector3f(1f, 0f, 0f)
        up.set(fallback).sub(Vector3f(forward).mul(fallback.dot(forward)))
    }
    if (!up.isFiniteVector() || up.lengthSquared() <= 1e-8f) return null
    return forward to up.normalize()
}

private fun Vector3f.isFiniteVector(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

/** Primitives are depth-tested, so geometry can occlude editor markers. */
object CinematicWorldRenderer {

    private data class Line(val a: Vec3, val b: Vec3, val argb: Int)
    private data class Frustum(val origin: Vec3, val forward: Vector3f, val up: Vector3f, val fov: Float, val aspect: Float, val near: Float, val argb: Int)
    private data class Diamond(val center: Vec3, val sizeBlocks: Float, val argb: Int)
    private data class AxisGizmo(val center: Vec3, val sizeBlocks: Float)
    private data class Cuboid(val min: Vec3, val max: Vec3, val argb: Int)

    private val lines = ArrayDeque<Line>()
    private val frustums = ArrayDeque<Frustum>()
    private val diamonds = ArrayDeque<Diamond>()
    private val axes = ArrayDeque<AxisGizmo>()
    private val cuboids = ArrayDeque<Cuboid>()

    fun queueLine(a: Vec3, b: Vec3, argb: Int) {
        lines.addLast(Line(a, b, argb))
    }

    fun queueFrustum(origin: Vec3, forward: Vector3f, up: Vector3f, fov: Float, aspect: Float, near: Float, argb: Int) {
        frustums.addLast(Frustum(origin, forward, up, fov, aspect, near, argb))
    }

    fun queueDiamond(center: Vec3, sizeBlocks: Float, argb: Int) {
        diamonds.addLast(Diamond(center, sizeBlocks, argb))
    }

    fun queueAxisGizmo(center: Vec3, sizeBlocks: Float) {
        axes.addLast(AxisGizmo(center, sizeBlocks))
    }

    fun queueCuboid(min: Vec3, max: Vec3, argb: Int) {
        cuboids.addLast(Cuboid(min, max, argb))
    }

    init {
        PlatformEvents.RENDER.subscribe { event ->
            if (event.stage != RenderEvent.Stage.TRANSLUCENT) return@subscribe
            if (!CobblemonImGui.isActive()) { drain(); return@subscribe }
            if (SceneEditorOverlay.activeScene == null) { drain(); return@subscribe }
            flush(event.poseStack, event.camera.position)
        }
    }

    private fun drain() {
        lines.clear(); frustums.clear(); diamonds.clear(); axes.clear(); cuboids.clear()
    }

    private fun flush(poseStack: PoseStack, cameraPos: Vec3) {
        val bufferSource = Minecraft.getInstance().renderBuffers().bufferSource()
        val consumer = bufferSource.getBuffer(RenderType.lines())

        poseStack.pushPose()
        try {
            val pose = poseStack.last()

            while (lines.isNotEmpty()) {
                val l = lines.removeFirst()
                addLine(consumer, pose, cameraPos, l.a, l.b, l.argb)
            }
            while (axes.isNotEmpty()) {
                val g = axes.removeFirst()
                addLine(consumer, pose, cameraPos, g.center, g.center.add(g.sizeBlocks.toDouble(), 0.0, 0.0), EditorTheme.AXIS_X)
                addLine(consumer, pose, cameraPos, g.center, g.center.add(0.0, g.sizeBlocks.toDouble(), 0.0), EditorTheme.AXIS_Y)
                addLine(consumer, pose, cameraPos, g.center, g.center.add(0.0, 0.0, g.sizeBlocks.toDouble()), EditorTheme.AXIS_Z)
            }
            while (diamonds.isNotEmpty()) {
                val d = diamonds.removeFirst()
                val s = d.sizeBlocks
                val c = d.center
                val top = c.add(0.0, s.toDouble(), 0.0)
                val bot = c.add(0.0, -s.toDouble(), 0.0)
                val x1 = c.add(s.toDouble(), 0.0, 0.0); val x2 = c.add(-s.toDouble(), 0.0, 0.0)
                val z1 = c.add(0.0, 0.0, s.toDouble()); val z2 = c.add(0.0, 0.0, -s.toDouble())
                addLine(consumer, pose, cameraPos, top, x1, d.argb)
                addLine(consumer, pose, cameraPos, x1, bot, d.argb)
                addLine(consumer, pose, cameraPos, bot, x2, d.argb)
                addLine(consumer, pose, cameraPos, x2, top, d.argb)
                addLine(consumer, pose, cameraPos, top, z1, d.argb)
                addLine(consumer, pose, cameraPos, z1, bot, d.argb)
                addLine(consumer, pose, cameraPos, bot, z2, d.argb)
                addLine(consumer, pose, cameraPos, z2, top, d.argb)
            }
            while (cuboids.isNotEmpty()) {
                val c = cuboids.removeFirst()
                val mn = c.min; val mx = c.max
                val a = Vec3(mn.x, mn.y, mn.z); val b = Vec3(mx.x, mn.y, mn.z)
                val cc = Vec3(mx.x, mn.y, mx.z); val d = Vec3(mn.x, mn.y, mx.z)
                val e = Vec3(mn.x, mx.y, mn.z); val f = Vec3(mx.x, mx.y, mn.z)
                val g = Vec3(mx.x, mx.y, mx.z); val h = Vec3(mn.x, mx.y, mx.z)
                addLine(consumer, pose, cameraPos, a, b, c.argb); addLine(consumer, pose, cameraPos, b, cc, c.argb)
                addLine(consumer, pose, cameraPos, cc, d, c.argb); addLine(consumer, pose, cameraPos, d, a, c.argb)
                addLine(consumer, pose, cameraPos, e, f, c.argb); addLine(consumer, pose, cameraPos, f, g, c.argb)
                addLine(consumer, pose, cameraPos, g, h, c.argb); addLine(consumer, pose, cameraPos, h, e, c.argb)
                addLine(consumer, pose, cameraPos, a, e, c.argb); addLine(consumer, pose, cameraPos, b, f, c.argb)
                addLine(consumer, pose, cameraPos, cc, g, c.argb); addLine(consumer, pose, cameraPos, d, h, c.argb)
            }
            while (frustums.isNotEmpty()) {
                val f = frustums.removeFirst()
                drawFrustum(consumer, pose, cameraPos, f)
            }
        } finally {
            try {
                poseStack.popPose()
            } finally {
                drain()
                bufferSource.endBatch(RenderType.lines())
            }
        }
    }

    private fun drawFrustum(consumer: VertexConsumer, pose: PoseStack.Pose, cameraPos: Vec3, f: Frustum) {
        val halfFov = Math.toRadians(f.fov.toDouble() / 2.0)
        val n = f.near
        val h = (Math.tan(halfFov) * n).toFloat()
        val w = h * f.aspect
        val (forwardUnit, upUnit) = frustumBasis(f.forward, f.up) ?: return
        val forward = Vector3f(forwardUnit).mul(n)
        val up = Vector3f(upUnit).mul(h)
        val right = Vector3f(forwardUnit).cross(upUnit).normalize().mul(w)

        val center = f.origin.add(forward.x.toDouble(), forward.y.toDouble(), forward.z.toDouble())
        val tl = center.add((-right.x + up.x).toDouble(), (-right.y + up.y).toDouble(), (-right.z + up.z).toDouble())
        val tr = center.add((right.x + up.x).toDouble(), (right.y + up.y).toDouble(), (right.z + up.z).toDouble())
        val bl = center.add((-right.x - up.x).toDouble(), (-right.y - up.y).toDouble(), (-right.z - up.z).toDouble())
        val br = center.add((right.x - up.x).toDouble(), (right.y - up.y).toDouble(), (right.z - up.z).toDouble())

        addLine(consumer, pose, cameraPos, f.origin, tl, f.argb)
        addLine(consumer, pose, cameraPos, f.origin, tr, f.argb)
        addLine(consumer, pose, cameraPos, f.origin, bl, f.argb)
        addLine(consumer, pose, cameraPos, f.origin, br, f.argb)
        addLine(consumer, pose, cameraPos, tl, tr, f.argb)
        addLine(consumer, pose, cameraPos, tr, br, f.argb)
        addLine(consumer, pose, cameraPos, br, bl, f.argb)
        addLine(consumer, pose, cameraPos, bl, tl, f.argb)
    }

    private fun addLine(
        consumer: VertexConsumer,
        pose: PoseStack.Pose,
        cameraPos: Vec3,
        a: Vec3,
        b: Vec3,
        argb: Int
    ) {
        val alpha = ((argb ushr 24) and 0xFF)
        val red   = ((argb ushr 16) and 0xFF)
        val green = ((argb ushr 8)  and 0xFF)
        val blue  =  (argb          and 0xFF)
        val relativeA = cameraRelativePosition(a, cameraPos)
        val relativeB = cameraRelativePosition(b, cameraPos)
        if (!relativeA.isFiniteVector() || !relativeB.isFiniteVector()) return
        val dir = Vector3f(relativeB).sub(relativeA)
        if (!dir.isFiniteVector()) return
        if (dir.lengthSquared() > 0f) dir.normalize()
        consumer.addVertex(pose.pose(), relativeA.x, relativeA.y, relativeA.z).setColor(red, green, blue, alpha).setNormal(pose, dir.x, dir.y, dir.z)
        consumer.addVertex(pose.pose(), relativeB.x, relativeB.y, relativeB.z).setColor(red, green, blue, alpha).setNormal(pose, dir.x, dir.y, dir.z)
    }
}

internal fun cameraRelativePosition(world: Vec3, camera: Vec3): Vector3f = Vector3f(
    (world.x - camera.x).toFloat(),
    (world.y - camera.y).toFloat(),
    (world.z - camera.z).toFloat()
)
