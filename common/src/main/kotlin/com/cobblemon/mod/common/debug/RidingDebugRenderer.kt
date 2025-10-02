package com.cobblemon.mod.common.debug

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.debug.DebugRenderer
import net.minecraft.core.BlockPos
import net.minecraft.core.Position
import java.util.function.Consumer

object RidingDebugRenderer : DebugRenderer.SimpleDebugRenderer {
    var enabled = false

    override fun render(
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        camX: Double,
        camY: Double,
        camZ: Double
    ) {
        val minecraft = Minecraft.getInstance() ?: return
        val vehicle = minecraft.player?.vehicle
        if (vehicle != null && vehicle is PokemonEntity) {
            DebugRenderer.renderFloatingText(poseStack, buffer, "riding", camX, camY, camZ, -1, 1f, false, 0.5f, true)
            renderRidingInfo(vehicle, poseStack, buffer)
        }
    }

    private fun renderRidingInfo(vehicle: PokemonEntity, poseStack: PoseStack, buffer: MultiBufferSource) {
        val ridingController = vehicle.ridingController
        if (ridingController != null) {
            var i = 0
            ridingController.context?.let { context ->
                val debugString = "Currently active: ${context.behaviour}"
                renderTextOverVehicle(poseStack, buffer, vehicle.position(), i, debugString, -1, 0.03f)
                i++
            }
            ridingController.behaviours.forEach { (style, settings) ->
                val debugString = "Available: $style"
                renderTextOverVehicle(poseStack, buffer, vehicle.position(), i, debugString, -1, 0.03f)
                i++
            }
        }
    }

    private fun renderTextOverVehicle(poseStack: PoseStack, buffer: MultiBufferSource, pos: Position, layer: Int, text: String, color: Int, scale: Float) {
        val blockPos = BlockPos.containing(pos)
        val x = blockPos.x.toDouble() + 0.5
        val y = blockPos.y.toDouble() + 2.4 + layer * 0.25
        val z = blockPos.z.toDouble() + 0.5
        DebugRenderer.renderFloatingText(poseStack, buffer, text, x, y, z, color, scale, false, 0.5f, true)
    }

    fun clientHandler(enabled: Boolean) {
        this.enabled = enabled
    }
}