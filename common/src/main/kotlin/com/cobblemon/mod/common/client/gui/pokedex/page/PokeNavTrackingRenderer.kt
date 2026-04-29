/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.page

import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import com.cobblemon.mod.common.util.cobblemonResource
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.roundToInt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.util.Mth
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.phys.AABB

object PokeNavTrackingRenderer {
    private val trackingTexture = cobblemonResource("textures/item/pokedexes/models/pokedex_screen_track.png")
    private const val FRAME_SIZE = 16

    fun render(context: GuiGraphics) {
        val player = Minecraft.getInstance().player ?: return
        val targetUuid = PokeNavClientState.trackedEntityUuid ?: return

        val holdsMain = player.mainHandItem.`is`(CobblemonItemTags.POKEDEX)
        val holdsOff = player.offhandItem.`is`(CobblemonItemTags.POKEDEX)
        if (!holdsMain && !holdsOff) {
            return
        }

        val searchBox = AABB.ofSize(player.position(), 256.0, 256.0, 256.0)
        val target = player.level().getEntities(player, searchBox) { it.uuid == targetUuid }.firstOrNull() ?: return

        val dx = target.x - player.x
        val dz = target.z - player.z
        val distance = target.distanceTo(player)

        val bearing = Math.toDegrees(atan2(dz, dx)).toFloat() - 90F
        val relativeYaw = Mth.wrapDegrees(bearing - player.yRot)
        val frameIndex = frameIndexFromRelativeYaw(relativeYaw)

        val rightSide = if (holdsMain) {
            player.mainArm == HumanoidArm.RIGHT
        } else {
            player.mainArm != HumanoidArm.RIGHT
        }

        val centerX = (context.guiWidth() / 2) + if (rightSide) 66 else -66
        val centerY = context.guiHeight() - 48

        context.fill(centerX - 11, centerY - 11, centerX + 11, centerY + 11, 0xA0202A33.toInt())

        val (r, g, b) = when {
            distance <= 15F -> Triple(0.42F, 1F, 0.42F)
            distance <= 35F -> Triple(1F, 0.92F, 0.35F)
            else -> Triple(1F, 0.34F, 0.34F)
        }

        blitk(
            matrixStack = context.pose(),
            texture = trackingTexture,
            x = centerX - 8,
            y = centerY - 8,
            width = FRAME_SIZE,
            height = FRAME_SIZE,
            textureWidth = FRAME_SIZE,
            textureHeight = FRAME_SIZE * 8,
            vOffset = frameIndex * FRAME_SIZE,
            red = r,
            green = g,
            blue = b
        )

        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = "${distance.roundToInt()}m".text().bold(),
            x = centerX,
            y = centerY + 4,
            centered = true,
            shadow = false
        )
    }

    private fun frameIndexFromRelativeYaw(relativeYaw: Float): Int {
        // Compass sectors centered on 0/45/90/... where 0 = forward.
        val forwardSector = floor((relativeYaw + 22.5F) / 45F).toInt()
        val normalized = Math.floorMod(forwardSector, 8)
        // Convert forward-based sectors into texture order:
        // Left, Left+Forward, Forward, Right+Forward, Right, Right+Back, Back, Left+Back.
        return Math.floorMod(normalized + 2, 8)
    }
}
