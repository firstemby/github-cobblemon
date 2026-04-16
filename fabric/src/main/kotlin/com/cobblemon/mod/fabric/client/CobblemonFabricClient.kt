/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.fabric.client

import com.cobblemon.mod.common.CobblemonClientImplementation
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.CobblemonClient.pokedexUsageContext
import com.cobblemon.mod.common.client.CobblemonClient.reloadCodedAssets
import com.cobblemon.mod.common.client.keybind.CobblemonKeyBinds
import com.cobblemon.mod.common.item.PokedexItem
import com.cobblemon.mod.common.particle.CobblemonParticles
import com.cobblemon.mod.common.particle.SnowstormParticleType
import com.cobblemon.mod.common.platform.events.ClientEntityEvent
import com.cobblemon.mod.common.platform.events.ClientPlayerEvent
import com.cobblemon.mod.common.platform.events.ClientTickEvent
import com.cobblemon.mod.common.platform.events.ItemTooltipEvent
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.platform.events.RenderEvent
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.isUsingPokedex
import com.cobblemon.mod.fabric.CobblemonFabric
import java.util.function.Supplier
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.fabricmc.fabric.api.resource.v1.ResourceLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.color.block.BlockTintSource
import net.minecraft.client.color.item.ItemTintSource
import net.minecraft.client.color.item.ItemTintSources
import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.client.model.geom.builders.LayerDefinition
import net.minecraft.client.particle.ParticleProvider
import net.minecraft.client.particle.SpriteSet
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.EntityRenderers
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleType
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.SimplePreparableReloadListener
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.util.profiling.ProfilerFiller
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType

class CobblemonFabricClient: ClientModInitializer, CobblemonClientImplementation {
    override fun onInitializeClient() {
        registerParticleFactory(CobblemonParticles.SNOWSTORM_PARTICLE_TYPE, SnowstormParticleType::Factory)
        CobblemonClient.initialize(this)

        CobblemonFabric.networkManager.registerClientHandlers()

        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(cobblemonResource("atlases"), object : SimplePreparableReloadListener<Unit>() {
            override fun prepare(resourceManager: ResourceManager, profiler: ProfilerFiller): Unit = Unit

            override fun apply(unit: Unit, resourceManager: ResourceManager, profiler: ProfilerFiller) {
                reloadCodedAssets(resourceManager)
            }
        })

        // Register the HUD extractRenderState callback for Pokédex
        HudElementRegistry.addLast(cobblemonResource("pokedex"), HudElement { graphics, tickDelta ->
            val client = Minecraft.getInstance()
            val player = client.player
            if (player != null) {
                if (player.isUsingPokedex() || pokedexUsageContext.transitionIntervals > 0) {
                    if (!player.isUsingItem) pokedexUsageContext.resetState(false)
                    pokedexUsageContext.renderUpdate(graphics, tickDelta)
                } else {
                    pokedexUsageContext.resetState()
                }
            }
        })

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { client ->
            val player = client.player
            if (player != null) {
                val itemStack = player.mainHandItem
                val offhandStack = player.offhandItem
                if (((itemStack.item is PokedexItem && player.usedItemHand == InteractionHand.MAIN_HAND) ||
                    (offhandStack.item is PokedexItem && player.usedItemHand == InteractionHand.OFF_HAND)) &&
                    player.isUsingItem &&
                    pokedexUsageContext.scanningGuiOpen
                ) {
                    val keyAttack = client.options.keyAttack
                    pokedexUsageContext.attackKeyHeld(keyAttack.isDown)
                }
            }
        })

        CobblemonKeyBinds.register(KeyMappingHelper::registerKeyMapping)

        ClientEntityEvents.ENTITY_LOAD.register { entity, level -> PlatformEvents.CLIENT_ENTITY_LOAD.post(ClientEntityEvent.Load(entity, level))}
        ClientEntityEvents.ENTITY_UNLOAD.register { entity, level -> PlatformEvents.CLIENT_ENTITY_UNLOAD.post(ClientEntityEvent.Unload(entity, level))}
        ClientTickEvents.START_CLIENT_TICK.register { client -> PlatformEvents.CLIENT_TICK_PRE.post(ClientTickEvent.Pre(client)) }
        ClientTickEvents.END_CLIENT_TICK.register { client -> PlatformEvents.CLIENT_TICK_POST.post(ClientTickEvent.Post(client)) }
        ClientPlayConnectionEvents.JOIN.register { _, _, client -> client.player?.let { PlatformEvents.CLIENT_PLAYER_LOGIN.post(ClientPlayerEvent.Login(it)) } }
        ClientPlayConnectionEvents.DISCONNECT.register { _, client -> client.player?.let { PlatformEvents.CLIENT_PLAYER_LOGOUT.post(ClientPlayerEvent.Logout(it)) } }
        ItemTooltipCallback.EVENT.register { stack, context, type, lines -> PlatformEvents.CLIENT_ITEM_TOOLTIP.post(ItemTooltipEvent(stack, context, type, lines)) }

        LevelRenderEvents.END_MAIN.register { context ->
            val client = Minecraft.getInstance()
            PlatformEvents.RENDER.post(
                RenderEvent(
                    stage = RenderEvent.Stage.TRANSLUCENT,
                    levelRenderer = context.levelRenderer(),
                    poseStack = context.poseStack(),
                    modelViewMatrix = context.poseStack().last().pose(),
                    projectionMatrix = context.levelState().cameraRenderState.projectionMatrix,
                    tickCounter = client.getDeltaTracker(),
                    camera = client.gameRenderer.getMainCamera()
                )
            )
        }

        CobblemonFabricItemModelRegistrations.register()
    }

    override fun registerLayer(modelLayer: ModelLayerLocation, supplier: Supplier<LayerDefinition>) {
        ModelLayerRegistry.registerModelLayer(modelLayer) { supplier.get() }
    }

    override fun <T : ParticleOptions> registerParticleFactory(type: ParticleType<T>, factory: (SpriteSet) -> ParticleProvider<T>) {
        ParticleProviderRegistry.getInstance().register(type,  { factory(it) })
    }

    override fun registerItemColors(identifier: Identifier, provider: ItemTintSource, vararg items: Item) {
        ItemTintSources.ID_MAPPER.put(identifier, provider.type())
    }

    override fun registerBlockColors(provider: BlockTintSource, vararg blocks: Block) {
        BlockColorRegistry.register(listOf(provider), *blocks)
    }

    override fun <T : BlockEntity> registerBlockEntityRenderer(type: BlockEntityType<out T>, factory: BlockEntityRendererProvider<T, *>) {
        BlockEntityRenderers.register(type, factory)
    }

    override fun <T : Entity> registerEntityRenderer(type: EntityType<out T>, factory: EntityRendererProvider<T>) {
        EntityRenderers.register(type, factory)
    }
}
