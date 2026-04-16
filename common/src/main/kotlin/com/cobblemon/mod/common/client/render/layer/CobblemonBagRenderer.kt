package com.cobblemon.mod.common.client.render.layer

import com.cobblemon.mod.common.item.bag.CobblemonBagItem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.model.PlayerModel
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.RenderLayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player

class CobblemonBagRenderer<T : Player>(renderLayerParent: RenderLayerParent<T, PlayerModel<T>>): RenderLayer<T, PlayerModel<T>>(renderLayerParent) {
    override fun render(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        player: T,
        limbSwing: Float,
        limbSwingAmount: Float,
        partialTick: Float,
        ageInTicks: Float,
        netHeadYaw: Float,
        headPitch: Float
    ) {
        val bagItem = player.getItemBySlot(EquipmentSlot.CHEST)
        if (bagItem.item !is CobblemonBagItem) return

        player.level().profiler.push("cobblemon_bag_render")
        poseStack.pushPose()

        val model =

        poseStack.popPose()
        player.level().profiler.pop()
    }
}