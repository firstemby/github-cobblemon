/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.entity

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.render.block.ScanIndicatorRenderer
import com.cobblemon.mod.common.entity.scan.ScanNodeEntity
import com.cobblemon.mod.common.pokedex.scanner.PokedexUsageContext
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.AABB

/**
 * Renders a [ScanNodeEntity] only while the viewing player holds a ScanNodeStick, as a small
 * wireframe cube plus the scannable/scanned billboard icon. Both obey the shared "one block only"
 * visibility gate, so a node is hidden behind real walls but visible through its own block.
 */
class ScanNodeRenderer(context: EntityRendererProvider.Context) : EntityRenderer<ScanNodeEntity>(context) {

    init {
        shadowRadius = 0f
    }

    override fun render(
        entity: ScanNodeEntity,
        entityYaw: Float,
        partialTicks: Float,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int
    ) {
        val player = Minecraft.getInstance().player ?: return
        val holdingStick = player.mainHandItem.`is`(CobblemonItems.SCAN_NODE_STICK) ||
            player.offhandItem.`is`(CobblemonItems.SCAN_NODE_STICK)
        val ctx = CobblemonClient.pokedexUsageContext
        val scanningMode = ctx.scanningGuiOpen && ctx.scannerMode == PokedexUsageContext.ScannerMode.SCANNING
        if (!holdingStick && !scanningMode) return

        val center = entity.boundingBox.center
        val eye = Minecraft.getInstance().gameRenderer.mainCamera.position
        if (!ScanIndicatorRenderer.isWithinOneBlock(entity.level(), eye, center, entity)) return

        val cy = ScanNodeEntity.SIZE / 2.0
        val scanned = CobblemonClient.clientScannedData.has(entity.category.bucket(), entity.scanKey())

        // Cube marker is the entity "model" — only shown to a builder holding the stick.
        if (holdingStick) {
            val s = 0.18
            val lineBuffer = buffer.getBuffer(RenderType.lines())
            val box = AABB(-s, cy - s, -s, s, cy + s, s)
            if (scanned) {
                LevelRenderer.renderLineBox(poseStack, lineBuffer, box, 0.40f, 0.95f, 0.55f, 0.85f)
            } else {
                LevelRenderer.renderLineBox(poseStack, lineBuffer, box, 0.45f, 0.90f, 0.95f, 0.85f)
            }
        }

        // Scan icon: shown to the builder always, and to anyone in scanning mode for a configured node
        // (mirrors how scannable block entities show their indicator while scanning).
        val showIcon = holdingStick || (scanningMode && entity.scanId.isNotBlank())
        if (showIcon) {
            poseStack.pushPose()
            poseStack.translate(0.0, cy, 0.0)
            ScanIndicatorRenderer.renderAt(poseStack, buffer, scanned, scale = 0.4f)
            poseStack.popPose()
        }
    }

    override fun getTextureLocation(entity: ScanNodeEntity): ResourceLocation = ScanIndicatorRenderer.UNSCANNED
}
