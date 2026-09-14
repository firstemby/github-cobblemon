package com.cobblemon.mod.common.client.render.stadium

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.util.lang
import net.minecraft.network.chat.Component
import com.cobblemon.mod.common.battles.stadium.StadiumSetup
import com.cobblemon.mod.common.battles.stadium.StadiumArena
import com.cobblemon.mod.common.battles.stadium.StadiumSpectatorArea
import com.cobblemon.mod.common.battles.stadium.StadiumConfiguration
import com.cobblemon.mod.common.block.entity.StadiumBlockEntity
import com.cobblemon.mod.common.item.stadium.StadiumPlacementWandItem
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.platform.events.RenderEvent
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.RenderStateShard.*
import net.minecraft.core.Direction
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.math.*

object PlacementHighlightRenderer {
    private val fieldLayer = RenderType.create("stadium_holograms", DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.QUADS, 4096, false, true, RenderType.CompositeState.builder()
            .setShaderState(POSITION_COLOR_SHADER).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
            .createCompositeState(false))
    private const val BLUE = 0x71DFFF
    private const val SIDE_TWO = 0xB5A1FF
    private const val INVALID = 0xFF687C
    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true
        PlatformEvents.RENDER.subscribe(Priority.NORMAL) { event ->
            if (event.stage == RenderEvent.Stage.TRANSLUCENT) render(event)
        }
    }

    private fun render(event: RenderEvent) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val tag = StadiumPlacementWandItem.getWandData(player.mainHandItem) ?: return
        val level = mc.level ?: return
        val pos = StadiumPlacementWandItem.stadiumPos(tag)
        if (player.blockPosition().distSqr(pos) > 10000 || tag.getString("Dimension") != level.dimension().location().toString()) return
        val block = level.getBlockEntity(pos) as? StadiumBlockEntity ?: return
        if (!tag.hasUUID("StadiumId") || tag.getUUID("StadiumId") != block.data.stadiumId) return
        val data = block.data
        val layout = tag.getString("LayoutType")
        val target = StadiumPlacementWandItem.target(tag)
        val time = (level.gameTime % 12000).toDouble() / 20
        val stack = event.poseStack
        val buffers = mc.renderBuffers().bufferSource()
        val labels = mutableListOf<Pair<Vec3, Component>>()
        stack.pushPose()
        try {
            val camera = event.camera.position
            val consumer = buffers.getBuffer(fieldLayer)
            val pose = stack.last()
            data.arena?.let { boundary(consumer, pose, it, BLUE, camera) }
            data.spectatorAreas.forEachIndexed { index, area ->
                bleacherBoundary(consumer, pose, area, 0x8BF2BE, camera)
                labels.add(area.bounds.center.add(0.0, 0.5, 0.0) to lang("stadium.highlight.bleachers", index + 1))
            }
            StadiumSetup.slots(data, layout).forEach { slot ->
                val placement = slot.placements(data, layout)?.getOrNull(slot.index) ?: return@forEach
                val color = if (slot == target) 0xE9FAFF else if (slot.side == 1) BLUE else SIDE_TWO
                val height = if (slot.type == "actors") 1.8 else 0.55
                marker(consumer, pose, placement.position, color, time, event, camera, height)
                labels.add(placement.position.add(0.0, height + 0.4, 0.0) to slot.label)
            }
            val hit = mc.hitResult as? BlockHitResult
            if (hit != null && hit.type == HitResult.Type.BLOCK && hit.direction == Direction.UP && hit.location.distanceToSqr(player.position()) <= 64.0 && target.type != "complete") {
                val point = Vec3(hit.blockPos.x + 0.5, hit.location.y, hit.blockPos.z + 0.5)
                val first = StadiumPlacementWandItem.firstCorner(tag)
                if (target.type == "bleachers") {
                    val area = StadiumSpectatorArea.fromCorners(first ?: point, point)
                    val valid = area.valid(Vec3.atLowerCornerOf(pos))
                    bleacherBoundary(consumer, pose, area, if (valid) 0x8BF2BE else INVALID, camera)
                    labels.add(point.add(0.0, 1.4, 0.0) to if (first == null) lang("stadium.highlight.bleachers_first") else lang("stadium.highlight.bleachers_second"))
                } else if (target.type == "arena") {
                    if (first == null) {
                        val valid = StadiumConfiguration.validPosition(point, Vec3.atLowerCornerOf(pos))
                        marker(consumer, pose, point, if (valid) BLUE else INVALID, time, event, camera)
                        labels.add(point.add(0.0, 1.0, 0.0) to lang("stadium.highlight.arena_first"))
                    } else {
                        val area = StadiumSetup.area(first, point)
                        val error = StadiumSetup.areaError(data, area, Vec3.atLowerCornerOf(pos))
                        boundary(consumer, pose, area, if (error == null) BLUE else INVALID, camera)
                        marker(consumer, pose, first, BLUE, time, event, camera)
                        labels.add(point.add(0.0, 1.4, 0.0) to (error ?: lang("stadium.highlight.arena_confirm", area.width.toInt(), area.depth.toInt())))
                    }
                } else {
                    val error = StadiumSetup.placementError(data, layout, target, point, Vec3.atLowerCornerOf(pos))
                    val height = if (target.type == "actors") 1.8 else 0.55
                    marker(consumer, pose, point, if (error == null) 0xD3F7FF else INVALID, time, event, camera, height)
                    labels.add(point.add(0.0, height + 0.65, 0.0) to (error ?: lang("stadium.highlight.place_slot", target.label)))
                }
            }
            buffers.endBatch(fieldLayer)
            for ((position, label) in labels) {
                stack.pushPose()
                try {
                    stack.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z)
                    stack.mulPose(event.camera.rotation())
                    stack.scale(-0.018f, -0.018f, 0.018f)
                    mc.font.drawInBatch(label.visualOrderText, -mc.font.width(label) / 2f, 0f, 0xFFE1F7FF.toInt(), false,
                        stack.last().pose(), buffers, Font.DisplayMode.NORMAL, 0x500D1B29, 15728880)
                } finally {
                    stack.popPose()
                }
            }
        } finally {
            stack.popPose()
            buffers.endBatch()
        }
    }

    private fun boundary(out: VertexConsumer, pose: PoseStack.Pose, arena: StadiumArena, color: Int, camera: Vec3) {
        val y = arena.center.y + 0.035
        val corners = listOf(Vec3(arena.minX, y, arena.minZ), Vec3(arena.maxX, y, arena.minZ),
            Vec3(arena.maxX, y, arena.maxZ), Vec3(arena.minX, y, arena.maxZ))
        repeat(4) { index ->
            val a = corners[index]; val b = corners[(index + 1) % 4]
            panel(out, pose, a, b, 1.2, color, 65, camera)
            panel(out, pose, a, b, 0.035, color, 225, camera)
        }
    }

    private fun bleacherBoundary(out: VertexConsumer, pose: PoseStack.Pose, area: StadiumSpectatorArea, color: Int, camera: Vec3) {
        val corners = listOf(area.min, Vec3(area.max.x, area.min.y, area.min.z),
            Vec3(area.max.x, area.min.y, area.max.z), Vec3(area.min.x, area.min.y, area.max.z))
        repeat(4) { index ->
            val a = corners[index]; val b = corners[(index + 1) % 4]
            val height = area.max.y - area.min.y
            panel(out, pose, a, b, height, color, 45, camera)
            panel(out, pose, a, b, 0.035, color, 220, camera)
            panel(out, pose, a.add(0.0, height, 0.0), b.add(0.0, height, 0.0), 0.035, color, 170, camera)
        }
    }

    private fun marker(out: VertexConsumer, pose: PoseStack.Pose, position: Vec3, color: Int,
                       time: Double, event: RenderEvent, camera: Vec3, height: Double = 0.55) {
        val base = position.add(0.0, 0.035, 0.0)
        val radius = 0.7
        val corners = listOf(Vec3(-radius, 0.0, -radius), Vec3(radius, 0.0, -radius),
            Vec3(radius, 0.0, radius), Vec3(-radius, 0.0, radius))
        repeat(4) { index ->
            val corner = corners[index]
            val next = corners[(index + 1) % 4]
            val a = base.add(corner)
            val b = base.add(next)
            panel(out, pose, a, b, height, color, 65, camera)
            panel(out, pose, a, b, 0.025, color, 220, camera)
            val innerA = base.add(corner.scale(0.8))
            val innerB = base.add(next.scale(0.8))
            quad(out, pose, listOf(innerA, innerB, b, a), color, listOf(0, 0, 100, 100), camera)
        }
        val cameraRight = event.camera.rotation().transform(Vector3f(0.025f, 0f, 0f))
        val cameraUp = event.camera.rotation().transform(Vector3f(0f, 0.025f, 0f))
        val right = Vec3(cameraRight.x.toDouble(), cameraRight.y.toDouble(), cameraRight.z.toDouble())
        val up = Vec3(cameraUp.x.toDouble(), cameraUp.y.toDouble(), cameraUp.z.toDouble())
        repeat(7) { index ->
            val progress = (time * 0.3 + index / 7.0 + abs(position.x * 0.07)) % 1
            val edge = index * 4.0 / 7
            val offset = corners[edge.toInt()].lerp(corners[(edge.toInt() + 1) % 4], edge % 1).scale(0.9)
            val center = base.add(offset).add(0.0, progress * (height + 0.5), 0.0)
            val alpha = (sin(progress * PI) * 180).toInt()
            quad(out, pose, listOf(center.subtract(right).subtract(up), center.add(right).subtract(up),
                center.add(right).add(up), center.subtract(right).add(up)), color, List(4) { alpha }, camera)
        }
    }

    private fun panel(out: VertexConsumer, pose: PoseStack.Pose, a: Vec3, b: Vec3, height: Double, color: Int, alpha: Int, camera: Vec3) =
        quad(out, pose, listOf(a, b, b.add(0.0, height, 0.0), a.add(0.0, height, 0.0)), color, listOf(alpha, alpha, 0, 0), camera)

    private fun quad(out: VertexConsumer, pose: PoseStack.Pose, points: List<Vec3>, color: Int, alpha: List<Int>, camera: Vec3) {
        points.forEachIndexed { index, p ->
            val relative = p.subtract(camera)
            out.addVertex(pose.pose(), relative.x.toFloat(), relative.y.toFloat(), relative.z.toFloat())
                .setColor((color shr 16) and 255, (color shr 8) and 255, color and 255, alpha[index])
        }
    }
}
