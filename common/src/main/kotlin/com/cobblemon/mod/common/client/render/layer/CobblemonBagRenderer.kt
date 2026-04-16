package com.cobblemon.mod.common.client.render.layer

import com.cobblemon.mod.common.client.CobblemonBakingOverrides
import com.cobblemon.mod.common.item.bag.CobblemonBagItem

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.model.PlayerModel
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.RenderLayer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemDisplayContext

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

        parentModel.body.translateAndRotate(poseStack)

        poseStack.rotateAround(Axis.ZP.rotationDegrees(180F), 0F, 1F, 0F)
        poseStack.translate(0.0, 1.0, 0.0)

        val model = CobblemonBakingOverrides.BAG_PENNY.getModel()
        Minecraft.getInstance().itemRenderer.render(
            bagItem,
            ItemDisplayContext.FIXED,
            false,
            poseStack,
            bufferSource,
            packedLight,
            OverlayTexture.NO_OVERLAY,
            model
        )

        poseStack.popPose()
        player.level().profiler.pop()
    }
}
