/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.stadium

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.block.entity.StadiumBlockEntity
import com.cobblemon.mod.common.item.stadium.StadiumPlacementWandItem
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.platform.events.RenderEvent
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * Renders colored outline boxes at configured placement positions when the player is holding a
 * [StadiumPlacementWandItem]. This gives spatial context while placing actors and pokemon.
 *
 * Color coding:
 * - Side 1 Actor: Blue
 * - Side 1 Pokemon: Cyan
 * - Side 2 Actor: Red
 * - Side 2 Pokemon: Orange
 *
 * @author Terrainwax
 * @since April 2026
 */
object PlacementHighlightRenderer {

    // Side 1 Actor: Blue
    private const val S1A_R = 0.267f; private const val S1A_G = 0.533f; private const val S1A_B = 1.0f
    // Side 1 Pokemon: Cyan
    private const val S1P_R = 0.267f; private const val S1P_G = 0.867f; private const val S1P_B = 1.0f
    // Side 2 Actor: Red
    private const val S2A_R = 1.0f; private const val S2A_G = 0.267f; private const val S2A_B = 0.267f
    // Side 2 Pokemon: Orange
    private const val S2P_R = 1.0f; private const val S2P_G = 0.533f; private const val S2P_B = 0.267f

    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true

        PlatformEvents.RENDER.subscribe(Priority.NORMAL) { event ->
            if (event.stage != RenderEvent.Stage.TRANSLUCENT) return@subscribe
            render(event)
        }
    }

    private fun render(event: RenderEvent) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val stack = player.mainHandItem

        if (stack.item !is StadiumPlacementWandItem) return

        val customData = stack.get(DataComponents.CUSTOM_DATA) ?: return
        val nbt = customData.copyTag()
        val stadiumPos = BlockPos(nbt.getInt("StadiumX"), nbt.getInt("StadiumY"), nbt.getInt("StadiumZ"))

        val level = mc.level ?: return
        val blockEntity = level.getBlockEntity(stadiumPos)
        if (blockEntity !is StadiumBlockEntity) return

        val data = blockEntity.data
        val layoutType = nbt.getString("LayoutType")
        val layout = if (layoutType == "singles") data.singlesLayout else data.doublesLayout
        layout ?: return

        val camPos = event.camera.position
        val poseStack = event.poseStack
        val bufferSource = mc.renderBuffers().bufferSource()
        val consumer = bufferSource.getBuffer(RenderType.lines())

        for (placement in layout.side1.actors) {
            renderMarkerOutline(poseStack, consumer, placement.position, camPos, S1A_R, S1A_G, S1A_B)
        }
        for (placement in layout.side1.pokemon) {
            renderMarkerOutline(poseStack, consumer, placement.position, camPos, S1P_R, S1P_G, S1P_B)
        }
        for (placement in layout.side2.actors) {
            renderMarkerOutline(poseStack, consumer, placement.position, camPos, S2A_R, S2A_G, S2A_B)
        }
        for (placement in layout.side2.pokemon) {
            renderMarkerOutline(poseStack, consumer, placement.position, camPos, S2P_R, S2P_G, S2P_B)
        }

        bufferSource.endBatch(RenderType.lines())
    }

    private fun renderMarkerOutline(
        poseStack: PoseStack,
        consumer: VertexConsumer,
        pos: Vec3,
        camPos: Vec3,
        r: Float, g: Float, b: Float
    ) {
        val box = AABB(
            pos.x - 0.25 - camPos.x, pos.y - camPos.y, pos.z - 0.25 - camPos.z,
            pos.x + 0.25 - camPos.x, pos.y + 1.8 - camPos.y, pos.z + 0.25 - camPos.z
        )
        LevelRenderer.renderLineBox(poseStack, consumer, box, r, g, b, 1.0f)
    }
}
