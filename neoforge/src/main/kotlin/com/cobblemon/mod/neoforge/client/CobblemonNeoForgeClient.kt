/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.neoforge.client

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonClientImplementation
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.CobblemonMenuType
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.CobblemonClient.pokedexUsageContext
import com.cobblemon.mod.common.client.CobblemonClient.reloadCodedAssets
import com.cobblemon.mod.common.client.CobblemonBakingOverrides
import com.cobblemon.mod.common.client.gui.config.CobblemonConfigScreen
import com.cobblemon.mod.common.client.gui.cookingpot.CookingPotScreen
import com.cobblemon.mod.common.client.keybind.CobblemonKeyBinds
import com.cobblemon.mod.common.api.pokeball.PokeBalls
import com.cobblemon.mod.common.client.pokedex.PokedexType
import com.cobblemon.mod.common.client.render.atlas.CobblemonAtlases
import com.cobblemon.mod.common.client.render.item.CobblemonItemTintSources
import com.cobblemon.mod.common.client.render.item.CobblemonModelPredicateRegistry
import com.cobblemon.mod.common.client.render.item.PokemonItemSpecialModelRenderer
import com.cobblemon.mod.common.client.render.models.blockbench.repository.BerryModelRepository
import com.cobblemon.mod.common.client.render.shader.CobblemonShaders
import com.cobblemon.mod.common.item.PokedexItem
import com.cobblemon.mod.common.particle.CobblemonParticles
import com.cobblemon.mod.common.particle.SnowstormParticleType
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.isUsingPokedex
import java.util.function.Supplier
import net.minecraft.client.Minecraft
import net.minecraft.client.color.block.BlockTintSource
import net.minecraft.client.color.item.ItemTintSource
import net.minecraft.client.resources.model.ModelDebugName
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.client.model.geom.builders.LayerDefinition
import net.minecraft.client.particle.ParticleProvider
import net.minecraft.client.particle.SpriteSet
import net.minecraft.client.resources.model.sprite.AtlasManager
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.EntityRenderers
import net.minecraft.client.resources.model.geometry.QuadCollection
import net.minecraft.resources.Identifier
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleType
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.neoforged.fml.ModContainer
import net.neoforged.fml.ModList
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.ClientHooks
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.ModelEvent
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent
import net.neoforged.neoforge.client.event.RegisterConditionalItemModelPropertyEvent
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent
import net.neoforged.neoforge.client.event.RegisterSelectItemModelPropertyEvent
import net.neoforged.neoforge.client.event.RegisterTextureAtlasesEvent
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey
import net.neoforged.neoforge.client.gui.IConfigScreenFactory
import net.neoforged.neoforge.client.gui.VanillaGuiLayers
import net.neoforged.neoforge.common.NeoForge
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS

object CobblemonNeoForgeClient : CobblemonClientImplementation {
    private data class QueuedReloadListener(
        val identifier: Identifier,
        val listener: PreparableReloadListener,
        val dependencies: Collection<Identifier>
    )

    private val reloadableResources = arrayListOf<QueuedReloadListener>()
    private val blockOverrideStandaloneKeys = mutableMapOf<Identifier, StandaloneModelKey<QuadCollection>>()

    fun init() {
        with(MOD_BUS) {
            addListener(::onClientSetup)
            addListener(::onKeyMappingRegister)
            addListener(::onRegisterParticleProviders)
            addListener(::onAddClientReloadListeners)
            addListener(::onRegisterTextureAtlases)
            addListener(::onRegisterConditionalItemModelProperties)
            addListener(::onRegisterSelectItemModelProperties)
            addListener(::onRegisterItemTintSources)
            addListener(::register3dModels)
            addListener(::onModelBakeCompleted)
            addListener(::onRegisterSpecialModelRenderers)
            addListener(::onTextureAtlasStitched)
        }
        with(NeoForge.EVENT_BUS) {
            addListener(::onRenderGuiOverlayEvent)
            addListener(::afterRenderGuiOverlayEvent)
            addListener(::afterEndClientTickEvent)
        }
    }

    private fun onClientSetup(event: FMLClientSetupEvent) {
        event.enqueueWork {
            CobblemonClient.initialize(this)
            CobblemonShaders.init()
            CobblemonModelPredicateRegistry.registerPredicates()
        }
        NeoForgeClientPlatformEventHandler.register()

        val modContainer = ModList.get().getModContainerById(Cobblemon.MODID).get()
        modContainer.registerExtensionPoint(IConfigScreenFactory::class.java, object : IConfigScreenFactory {
            override fun createScreen(modContainer: ModContainer, arg: Screen): Screen = CobblemonConfigScreen(arg)
        })
    }

    override fun registerLayer(modelLayer: ModelLayerLocation, supplier: Supplier<LayerDefinition>) {
        ClientHooks.registerLayerDefinition(modelLayer, supplier)
    }

    override fun <T : ParticleOptions> registerParticleFactory(type: ParticleType<T>, factory: (SpriteSet) -> ParticleProvider<T>) {
        throw UnsupportedOperationException("Forge can't store these early, use CobblemonForgeClient#onRegisterParticleProviders")
    }

    @Suppress("DEPRECATION")
    override fun registerItemColors(identifier: Identifier, provider: ItemTintSource, vararg items: Item) {
        // TODO 1.21.11: item tint registration moved to ItemTintSource-driven model data.
        // Keep this as a no-op compile bridge until the item model pass is rebuilt.
    }

    @Suppress("DEPRECATION")
    override fun registerBlockColors(provider: BlockTintSource, vararg blocks: Block) {
        Minecraft.getInstance().blockColors.register(listOf(provider), *blocks)
    }

    override fun <T : BlockEntity> registerBlockEntityRenderer(type: BlockEntityType<out T>, factory: BlockEntityRendererProvider<T, *>) {
        BlockEntityRenderers.register(type, factory)
    }

    override fun <T : Entity> registerEntityRenderer(type: EntityType<out T>, factory: EntityRendererProvider<T>) {
        EntityRenderers.register(type, factory)
    }

    private fun register3dModels(event: ModelEvent.RegisterStandalone) {
        PokeBalls.all().forEach { pokeBall ->
            registerStandaloneModel(event, pokeBall.model3d)
        }
        PokedexType.entries.forEach { pokedex ->
            registerStandaloneModel(event, pokedex.getItemModelPath())
            registerStandaloneModel(event, pokedex.getItemModelPath("scanning"))
            registerStandaloneModel(event, pokedex.getItemModelPath("flat"))
            registerStandaloneModel(event, pokedex.getItemModelPath("flat_off"))
            registerStandaloneModel(event, pokedex.getItemModelPath("off"))
        }
        CobblemonItems.wearables.forEach { wearable ->
            registerStandaloneModel(event, wearable.getModel3d())
        }
        CobblemonBakingOverrides.models.forEach { bakingOverride ->
            registerStandaloneQuadCollectionModel(event, bakingOverride.modelLocation)
        }
    }

    private fun onKeyMappingRegister(event: RegisterKeyMappingsEvent) {
        CobblemonKeyBinds.register(event::register)
    }

    private fun onRegisterParticleProviders(event: RegisterParticleProvidersEvent) {
        event.registerSpriteSet(CobblemonParticles.SNOWSTORM_PARTICLE_TYPE, SnowstormParticleType::Factory)
    }

    private fun onRegisterSpecialModelRenderers(event: RegisterSpecialModelRendererEvent) {
        event.register(cobblemonResource("pokemon"), PokemonItemSpecialModelRenderer.Unbaked.MAP_CODEC)
    }

    private fun registerStandaloneModel(event: ModelEvent.RegisterStandalone, modelId: Identifier) {
        event.register(
            StandaloneModelKey({ modelId.toString() }),
            SimpleUnbakedStandaloneModel.simpleModelWrapper(modelId)
        )
    }

    private fun registerStandaloneQuadCollectionModel(event: ModelEvent.RegisterStandalone, modelId: Identifier) {
        val key = StandaloneModelKey<QuadCollection>({ modelId.toString() })
        blockOverrideStandaloneKeys[modelId] = key
        event.register(
            key,
            SimpleUnbakedStandaloneModel.quadCollection(modelId)
        )
    }

    private fun onModelBakeCompleted(event: ModelEvent.BakingCompleted) {
        val standaloneModels = event.bakingResult.standaloneModels()
        CobblemonBakingOverrides.models.forEach { bakingOverride ->
            val key = blockOverrideStandaloneKeys[bakingOverride.modelLocation] ?: return@forEach
            bakingOverride.setModel(standaloneModels.get(key) ?: QuadCollection.EMPTY)
        }
    }

    private fun onRegisterConditionalItemModelProperties(event: RegisterConditionalItemModelPropertyEvent) {
        event.register(CobblemonModelPredicateRegistry.ROD_CAST, CobblemonModelPredicateRegistry.PokerodCastProperty.MAP_CODEC)
    }

    private fun onRegisterSelectItemModelProperties(event: RegisterSelectItemModelPropertyEvent) {
        event.register(CobblemonModelPredicateRegistry.PONIGIRI_OVERLAY, CobblemonModelPredicateRegistry.PonigiriOverlayProperty.TYPE)
        event.register(CobblemonModelPredicateRegistry.APRIJUICE_QUALITY, CobblemonModelPredicateRegistry.AprijuiceQualityProperty.TYPE)
    }

    private fun onRegisterItemTintSources(event: RegisterColorHandlersEvent.ItemTintSources) {
        event.register(CobblemonItemTintSources.FOOD_COLOUR_MIX, CobblemonItemTintSources.FoodColourMixTintSource.MAP_CODEC)
        event.register(CobblemonItemTintSources.FOOD_COLOUR_ENTRY, CobblemonItemTintSources.FoodColourEntryTintSource.MAP_CODEC)
        event.register(CobblemonItemTintSources.RIDE_BOOST_MIX, CobblemonItemTintSources.RideBoostMixTintSource.MAP_CODEC)
    }

    private fun onAddClientReloadListeners(event: AddClientReloadListenersEvent) {
        event.addListener(cobblemonResource("coded_assets"), object : ResourceManagerReloadListener {
            override fun onResourceManagerReload(manager: ResourceManager) {
                reloadCodedAssets(manager)
            }
        })
        this.reloadableResources.forEach { queued ->
            event.addListener(queued.identifier, queued.listener)
        }
        this.reloadableResources.forEach { queued ->
            queued.dependencies.forEach { dependency ->
                event.addDependency(dependency, queued.identifier)
            }
        }
    }

    private fun onRegisterTextureAtlases(event: RegisterTextureAtlasesEvent) {
        CobblemonAtlases.atlases.forEach { atlas ->
            event.register(AtlasManager.AtlasConfig(atlas.atlasId, atlas.sourcePath, false))
        }
    }

    private fun onTextureAtlasStitched(event: TextureAtlasStitchedEvent) {
        CobblemonAtlases.atlases.forEach { atlas ->
            if (atlas.bind(event.atlas)) {
                BerryModelRepository.patchModels()
            }
        }
    }

    private fun onRenderGuiOverlayEvent(event: RenderGuiLayerEvent.Pre) {
        if (event.name == VanillaGuiLayers.CHAT) {
            CobblemonClient.beforeChatRender(event.guiGraphics, event.partialTick)
        }
    }

    private fun afterRenderGuiOverlayEvent(event: RenderGuiEvent.Post) {
        val client = Minecraft.getInstance()
        val player = client.player
        if (player != null) {
            if (player.isUsingPokedex() || pokedexUsageContext.transitionIntervals > 0) {
                if (!player.isUsingItem) pokedexUsageContext.resetState(false)
                pokedexUsageContext.renderUpdate(event.guiGraphics, event.partialTick)
            } else {
                pokedexUsageContext.resetState()
            }
        }
    }

    private fun afterEndClientTickEvent(event: ClientTickEvent.Post) {
        val client = Minecraft.getInstance()
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
    }

    internal fun registerResourceReloader(identifier: Identifier, reloader: PreparableReloadListener, dependencies: Collection<Identifier>) {
        this.reloadableResources += QueuedReloadListener(identifier, reloader, dependencies)
    }
}
