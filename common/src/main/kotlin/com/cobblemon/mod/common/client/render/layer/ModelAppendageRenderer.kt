package com.cobblemon.mod.common.client.render.layer

import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel
import com.cobblemon.mod.common.client.render.models.blockbench.repository.PokemonModelRepository
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.client.render.models.blockbench.repository.appendage.ModelAppendageModelRepository
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.resources.model.ModelResourceLocation
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemDisplayContext
import org.joml.Vector3f

object ModelAppendageRenderer {

    fun <T> render(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        livingEntity: T,
        limbSwing: Float,
        limbSwingAmount: Float,
        ageInTicks: Float,
        netHeadYaw: Float,
        headPitch: Float,
        locator: String,
        context: RenderContext,
        parentModel: ResourceLocation
    ) where T : PokemonEntity {
        val clientDelegate = livingEntity.delegate as PokemonClientDelegate
        val appendages = ModelAppendageModelRepository.getAppendagesForModelAndTarget(parentModel, locator) ?: return
        // avoid editing the original context
        val clonedContext = context.clone()
        appendages.forEach { appendage ->
            val modelObj = appendage.model.resolveObject(clientDelegate.runtime).obj
            if (modelObj is ResourceLocation) {
                poseStack.pushPose()
                clientDelegate.locatorStates[locator]?.let { bone ->
                    val stack = if(modelObj.toString() == "cobblemon:held_item") livingEntity.pokemon.heldItemNoCopy() else BuiltInRegistries.ITEM.get(modelObj).defaultInstance
                    val pos = bone.getOrigin().subtract(livingEntity.position())
                    val scale = Vector3f()
                    bone.matrix.getScale(scale)
                    poseStack.scale(scale.x(), scale.y(), scale.z())
                    poseStack.translate(pos.x, pos.y, pos.z)
                    poseStack.mulPose(bone.clone().matrix.setTranslation(0.0f, 0.0f, 0.0f))
                    poseStack.scale(
                        appendage.scale.x().toFloat(), appendage.scale.y().toFloat(),
                        appendage.scale.z().toFloat()
                    )
                    poseStack.mulPose(Axis.XP.rotationDegrees(appendage.rotation.x().toFloat()))
                    poseStack.mulPose(Axis.YP.rotationDegrees(appendage.rotation.y().toFloat()))
                    poseStack.mulPose(Axis.ZP.rotationDegrees(appendage.rotation.z().toFloat()))
                    poseStack.translate(appendage.offset.x(), appendage.offset.y(), appendage.offset.z())
                    Minecraft.getInstance().itemRenderer.renderStatic(stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, packedLight, OverlayTexture.NO_OVERLAY, poseStack, bufferSource, livingEntity.level(), 0)
                }
                poseStack.popPose()
            } else if (modelObj is Pair<*, *>) {
                val repo = modelObj.first as ModelAppendageModelRepository
                val resLoc = modelObj.second as ResourceLocation
                val model =
                    repo.getPoserNullable(resLoc, clientDelegate) ?: return
                model.context = clonedContext
                model.setLayerContext(
                    bufferSource,
                    clientDelegate,
                    ModelAppendageModelRepository.getLayers(resLoc, clientDelegate)
                )
                poseStack.pushPose()
                clientDelegate.locatorStates[locator]?.let { bone ->
                    val pos = bone.getOrigin().subtract(livingEntity.position())
                    val scale = Vector3f()
                    bone.matrix.getScale(scale)
                    poseStack.scale(scale.x(), scale.y(), scale.z())
                    poseStack.translate(pos.x, pos.y, pos.z)
                    poseStack.mulPose(bone.clone().matrix.setTranslation(0.0f, 0.0f, 0.0f))
                    poseStack.mulPose(Axis.XP.rotationDegrees(180f))
                    poseStack.translate(0.0, -1.75, 0.0)
                    poseStack.scale(
                        appendage.scale.x().toFloat(), appendage.scale.y().toFloat(),
                        appendage.scale.z().toFloat()
                    )
                    poseStack.mulPose(Axis.XP.rotationDegrees(appendage.rotation.x().toFloat()))
                    poseStack.mulPose(Axis.YP.rotationDegrees(appendage.rotation.y().toFloat()))
                    poseStack.mulPose(Axis.ZP.rotationDegrees(appendage.rotation.z().toFloat()))
                    poseStack.translate(appendage.offset.x(), appendage.offset.y(), appendage.offset.z())
                    model.applyAnimations(
                        livingEntity,
                        clientDelegate,
                        limbSwing,
                        limbSwingAmount,
                        ageInTicks,
                        netHeadYaw,
                        headPitch
                    )
                    val buffer = bufferSource.getBuffer(
                        RenderType.entityCutout(
                            ModelAppendageModelRepository.getTexture(
                                resLoc,
                                clientDelegate
                            )
                        )
                    )
                    model.withLayerContext(
                        bufferSource,
                        clientDelegate,
                        ModelAppendageModelRepository.getLayers(resLoc, clientDelegate)
                    ) {
                        model.render(clonedContext, poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, -0x1)
                    }

                }
                model.green = 1F
                model.blue = 1F
                model.red = 1F

                model.resetLayerContext()
                poseStack.popPose()
            }
        }
    }
}