/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.shader

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.integration.DeferredMeshes
import com.cobblemon.mod.common.integration.IrisCompatibility
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.PostChain
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation

object TeraHalo {
    private val CHAIN_LOCATION = cobblemonResource("shaders/post/tera_halo.json")

    private val deferred = DeferredMeshes()
    private var chain: PostChain? = null

    private val MASK_TARGET = RenderStateShard.OutputStateShard(
        "cobblemon_tera_halo_mask",
        { chain?.getTempTarget("mask")?.bindWrite(false) },
        { Minecraft.getInstance().mainRenderTarget.bindWrite(false) }
    )

    private val renderType = Util.memoize { texture: ResourceLocation ->
        RenderType.create(
            "cobblemon_tera_halo",
            DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS,
            256,
            false,
            false,
            RenderType.CompositeState.builder()
                .setShaderState(RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeOutlineShader))
                .setTextureState(RenderStateShard.TextureStateShard(texture, false, false))
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setCullState(RenderStateShard.CULL)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setOutputState(MASK_TARGET)
                .createCompositeState(false)
        )
    }

    fun renderType(texture: ResourceLocation): RenderType = renderType.apply(texture)

    fun record(renderType: RenderType, draw: (VertexConsumer) -> Unit) {
        if (IrisCompatibility.isRenderingShadowPass()) return
        deferred.record(renderType, draw)
    }

    fun draw() {
        if (deferred.isEmpty()) return
        val chain = chain ?: return deferred.discard()
        val main = Minecraft.getInstance().mainRenderTarget

        val mask = chain.getTempTarget("mask")
        mask.setClearColor(0F, 0F, 0F, 0F)
        mask.clear(Minecraft.ON_OSX)
        mask.copyDepthFrom(main)
        deferred.drawAll()

        chain.process(0F)
        main.bindWrite(false)
        RenderSystem.enableBlend()
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR)
        chain.getTempTarget("swap").blitToScreen(main.width, main.height, false)
        RenderSystem.disableBlend()
        RenderSystem.defaultBlendFunc()
    }


    fun load() {
        close()
        val minecraft = Minecraft.getInstance()
        chain = try {
            PostChain(minecraft.textureManager, minecraft.resourceManager, minecraft.mainRenderTarget, CHAIN_LOCATION).also {
                it.resize(minecraft.mainRenderTarget.width, minecraft.mainRenderTarget.height)
            }
        } catch (e: Exception) {
            Cobblemon.LOGGER.error("Failed to load tera halo post chain $CHAIN_LOCATION", e)
            null
        }
    }

    fun resize(width: Int, height: Int) {
        chain?.resize(width, height)
    }

    fun close() {
        chain?.close()
        chain = null
    }
}
