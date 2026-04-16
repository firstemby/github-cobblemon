/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.Cobblemon.LOGGER
import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.CobblemonClientImplementation
import com.cobblemon.mod.common.CobblemonEntities
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.CobblemonMenuType
import com.cobblemon.mod.common.ModAPI
import com.cobblemon.mod.common.api.berry.Berries
import com.cobblemon.mod.common.api.scheduling.ClientTaskTracker
import com.cobblemon.mod.common.api.storage.player.client.ClientGeneralPlayerData
import com.cobblemon.mod.common.api.storage.player.client.ClientPokedexManager
import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import com.cobblemon.mod.common.block.entity.TintBlockEntity
import com.cobblemon.mod.common.client.battle.ClientBattle
import com.cobblemon.mod.common.client.gui.PartyOverlay
import com.cobblemon.mod.common.client.gui.PartyOverlayDataControl
import com.cobblemon.mod.common.client.gui.RideControlsOverlay
import com.cobblemon.mod.common.client.gui.battle.BattleOverlay
import com.cobblemon.mod.common.client.gui.party.PartyTutorialToasts
import com.cobblemon.mod.common.client.gui.toast.ToastTracker
import com.cobblemon.mod.common.client.particle.BedrockParticleOptionsRepository
import com.cobblemon.mod.common.client.render.ClientPlayerIcon
import com.cobblemon.mod.common.client.render.DeferredRenderer
import com.cobblemon.mod.common.client.render.block.BerryBlockRenderer
import com.cobblemon.mod.common.client.render.block.CampfireBlockEntityRenderer
import com.cobblemon.mod.common.client.render.block.DisplayCaseRenderer
import com.cobblemon.mod.common.client.render.block.FossilAnalyzerRenderer
import com.cobblemon.mod.common.client.render.block.GildedChestBlockRenderer
import com.cobblemon.mod.common.client.render.block.HealingMachineRenderer
import com.cobblemon.mod.common.client.render.block.LecternBlockEntityRenderer
import com.cobblemon.mod.common.client.render.block.PokeSnackBlockEntityRenderer
import com.cobblemon.mod.common.client.render.block.RestorationTankRenderer
import com.cobblemon.mod.common.client.render.entity.PokeBobberEntityRenderer
import com.cobblemon.mod.common.client.render.generic.GenericBedrockRenderer
import com.cobblemon.mod.common.client.render.layer.PokemonOnShoulderRenderer
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockAnimationRepository
import com.cobblemon.mod.common.client.render.models.blockbench.repository.BerryModelRepository
import com.cobblemon.mod.common.client.render.models.blockbench.repository.MiscModelRepository
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.mixin.client.accessor.LivingEntityRendererAccessor
import com.cobblemon.mod.common.client.render.npc.NPCRenderer
import com.cobblemon.mod.common.client.render.pokeball.PokeBallRenderer
import com.cobblemon.mod.common.client.render.pokemon.PokemonRenderer
import com.cobblemon.mod.common.client.requests.ClientPlayerActionRequests
import com.cobblemon.mod.common.client.sound.BattleMusicController
import com.cobblemon.mod.common.client.sound.EntitySoundTracker
import com.cobblemon.mod.common.client.storage.ClientStorageManager
import com.cobblemon.mod.common.client.net.SyncShoulderEntityHandler
import com.cobblemon.mod.common.client.tooltips.AprijuiceTooltipGenerator
import com.cobblemon.mod.common.client.tooltips.CobblemonTooltipGenerator
import com.cobblemon.mod.common.client.tooltips.FishingBaitTooltipGenerator
import com.cobblemon.mod.common.client.tooltips.FishingRodTooltipGenerator
import com.cobblemon.mod.common.client.tooltips.PokePuffTooltipGenerator
import com.cobblemon.mod.common.client.tooltips.RecipeSeasoningAbsorptionTooltipGenerator
import com.cobblemon.mod.common.client.tooltips.SeasoningTooltipGenerator
import com.cobblemon.mod.common.client.tooltips.TooltipManager
import com.cobblemon.mod.common.client.trade.ClientTrade
import com.cobblemon.mod.common.client.gui.cookingpot.CookingPotScreen
import com.cobblemon.mod.common.client.render.color.AprijuiceItemColorProvider
import com.cobblemon.mod.common.client.render.color.PokeBaitItemColorProvider
import com.cobblemon.mod.common.client.render.color.PokeSnackItemColorProvider
import com.cobblemon.mod.common.client.render.color.PonigiriItemColorProvider
import com.cobblemon.mod.common.client.render.color.SinisterTeaItemColorProvider
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.pokedex.scanner.PokedexUsageContext
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.isLookingAt
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.color.block.BlockTintSource
import net.minecraft.client.model.player.PlayerModel
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.MenuScreens
import net.minecraft.client.renderer.block.BlockAndTintGetter
import net.minecraft.client.renderer.blockentity.HangingSignRenderer
import net.minecraft.client.renderer.blockentity.StandingSignRenderer
import net.minecraft.client.renderer.entity.player.AvatarRenderer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.core.BlockPos
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.PlayerModelType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB

object CobblemonClient {

    lateinit var implementation: CobblemonClientImplementation
    val storage = ClientStorageManager()
    var trade: ClientTrade? = null
    var battle: ClientBattle? = null
    var clientPlayerData = ClientGeneralPlayerData()
    var clientPokedexData = ClientPokedexManager(mutableMapOf())

    /** If true then we won't bother them anymore about choosing a starter even if it's a thing they can do. */
    var checkedStarterScreen = false
    var lastPcBoxViewed = 0
    var requests = ClientPlayerActionRequests()
    var teamData = ClientPlayerTeamData()
    val overlay: PartyOverlay by lazy { PartyOverlay() }
    val battleOverlay: BattleOverlay by lazy { BattleOverlay() }
    val pokedexUsageContext: PokedexUsageContext by lazy { PokedexUsageContext() }
    val rideControlsOverlay: RideControlsOverlay by lazy { RideControlsOverlay() }

    fun onLogin() {
        clientPlayerData = ClientGeneralPlayerData()
        requests = ClientPlayerActionRequests()
        teamData = ClientPlayerTeamData()
        clientPokedexData = ClientPokedexManager(mutableMapOf())
        storage.onLogin()
//        CobblemonDataProvider.canReload = false
    }

    fun onLogout() {
        storage.onLogout()
        battle = null
        battleOverlay.onLogout()
        SyncShoulderEntityHandler.clear()
        ClientTaskTracker.clear()
        checkedStarterScreen = false
        overlay.resetAttachedToast()
        PartyTutorialToasts.reset()
        ToastTracker.reset()
//        CobblemonDataProvider.canReload = true
        DeferredRenderer.clearAll()
        ClientPlayerIcon.clear()
    }

    fun initialize(implementation: CobblemonClientImplementation) {
        LOGGER.info("Initializing Cobblemon client")
        this.implementation = implementation

        PlatformEvents.CLIENT_PLAYER_LOGIN.subscribe { onLogin() }
        PlatformEvents.CLIENT_PLAYER_LOGOUT.subscribe { onLogout() }

        this.registerBlockEntityRenderers()
        registerColors()
        registerFlywheelRenderers()
        this.registerEntityRenderers()
        this.registerItemColors()
        Berries.observable.subscribe {
            BerryModelRepository.patchModels()
        }
        this.registerTooltipManagers()
        this.registerMenuScreens()

        PlatformEvents.CLIENT_ITEM_TOOLTIP.subscribe { event ->
            val stack = event.stack
            val lines = event.lines
            TooltipManager.generateTooltips(stack, lines, Minecraft.getInstance().hasShiftDown())
        }

        PlatformEvents.CLIENT_ENTITY_LOAD.subscribe { event -> SyncShoulderEntityHandler.tryApply(event.entity) }
        PlatformEvents.CLIENT_ENTITY_UNLOAD.subscribe { event -> EntitySoundTracker.clear(event.entity.uuid) }
        PlatformEvents.CLIENT_TICK_POST.subscribe { event ->
            val player = event.client.player
            if (player != null) {
                val selectedItem = player.inventory.selectedItem
                if (pokedexUsageContext.scanningGuiOpen &&
                    !(selectedItem.`is`(CobblemonItemTags.POKEDEX)) &&
                    !(player.offhandItem.`is`(CobblemonItemTags.POKEDEX) &&
                            player.isUsingItem &&
                            player.usedItemHand == InteractionHand.OFF_HAND)
                ) {
                    // Stop using Pokédex in main hand if player switches to a different slot in hotbar
                    pokedexUsageContext.stopUsing(PokedexUsageContext.OPEN_SCANNER_BUFFER_TICKS + 1)
                }
                if (event.client.isPaused) {
                    return@subscribe
                }

                val nearbyPokemon = player.level().getEntities(
                    player,
                    AABB.ofSize(player.position(), 16.0, 16.0, 16.0)
                ) { it is PokemonEntity }

                nearbyPokemon.forEach { entity ->
                    if (entity is PokemonEntity && !entity.isSilent && !entity.passengers.contains(player)) {
                        if (player.isLookingAt(entity) && !player.isSpectator && entity.pokemon.shiny) entity.delegate.spawnShinyParticle(player)
                        entity.delegate.spawnAspectParticle()
                    }
                }
            }
            ClientPlayerIcon.onTick()
            PartyOverlayDataControl.tick(event.client.isPaused)
        }
    }

    private fun registerTooltipManagers() {
        TooltipManager.registerTooltipGenerator(CobblemonTooltipGenerator)
        TooltipManager.registerTooltipGenerator(RecipeSeasoningAbsorptionTooltipGenerator)
        TooltipManager.registerTooltipGenerator(FishingBaitTooltipGenerator)
        TooltipManager.registerTooltipGenerator(SeasoningTooltipGenerator)
        TooltipManager.registerTooltipGenerator(FishingRodTooltipGenerator)
        TooltipManager.registerTooltipGenerator(AprijuiceTooltipGenerator)
        TooltipManager.registerTooltipGenerator(PokePuffTooltipGenerator)
    }

    fun registerFlywheelRenderers() {
//        InstancedRenderRegistry
//            .configure(CobblemonBlockEntities.BERRY)
//            .alwaysSkipRender()
//            .factory(::BerryEntityInstance)
//            .apply()
    }

    fun registerColors() {
        this.implementation.registerBlockColors(object : BlockTintSource {
            override fun color(state: BlockState): Int {
                return 0xFFFFFF
            }

            override fun colorInWorld(state: BlockState, level: BlockAndTintGetter, pos: BlockPos): Int {
                level.getBlockEntity(pos)?.let { blockEntity ->
                    if (blockEntity is TintBlockEntity) return blockEntity.getTint()
                }
                return super.colorInWorld(state, level, pos)
            }
        }, CobblemonBlocks.POKE_SNACK, CobblemonBlocks.POKE_CAKE)
    }

    fun beforeChatRender(context: GuiGraphicsExtractor, tickCounter: DeltaTracker) {
        if (battle == null) {
            overlay.extractRenderState(context, tickCounter)
        } else {
            battleOverlay.extractRenderState(context, tickCounter)
        }
        rideControlsOverlay.extractRenderState(context, tickCounter)
    }

    @Suppress("UNCHECKED_CAST")
    fun onAddLayer(skinMap: Map<PlayerModelType, AvatarRenderer<AbstractClientPlayer>>?) {
        if (skinMap.isNullOrEmpty()) {
            return
        }
        skinMap[PlayerModelType.WIDE]?.let { renderer ->
            if ((renderer as LivingEntityRendererAccessor<AvatarRenderState, PlayerModel>).`cobblemon$getLayers`().none { it is PokemonOnShoulderRenderer }) {
                renderer.addLayer(PokemonOnShoulderRenderer(renderer))
            }
        }
        skinMap[PlayerModelType.SLIM]?.let { renderer ->
            if ((renderer as LivingEntityRendererAccessor<AvatarRenderState, PlayerModel>).`cobblemon$getLayers`().none { it is PokemonOnShoulderRenderer }) {
                renderer.addLayer(PokemonOnShoulderRenderer(renderer))
            }
        }
    }

    private fun registerMenuScreens() {
        MenuScreens.register(CobblemonMenuType.COOKING_POT, ::CookingPotScreen)
    }

    private fun registerBlockEntityRenderers() {
        this.implementation.registerBlockEntityRenderer(
            CobblemonBlockEntities.HEALING_MACHINE,
            ::HealingMachineRenderer
        )
        this.implementation.registerBlockEntityRenderer(CobblemonBlockEntities.BERRY, ::BerryBlockRenderer)
        this.implementation.registerBlockEntityRenderer(CobblemonBlockEntities.SIGN) { context ->
            StandingSignRenderer(context) as net.minecraft.client.renderer.blockentity.BlockEntityRenderer<
                    com.cobblemon.mod.common.block.entity.CobblemonSignBlockEntity,
                    net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
                    >
        }

        this.implementation.registerBlockEntityRenderer(CobblemonBlockEntities.HANGING_SIGN) { context ->
            HangingSignRenderer(context) as net.minecraft.client.renderer.blockentity.BlockEntityRenderer<
                com.cobblemon.mod.common.block.entity.CobblemonHangingSignBlockEntity,
                net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
                >
        }
        this.implementation.registerBlockEntityRenderer(CobblemonBlockEntities.FOSSIL_ANALYZER, ::FossilAnalyzerRenderer)
        this.implementation.registerBlockEntityRenderer(CobblemonBlockEntities.RESTORATION_TANK, ::RestorationTankRenderer)
        this.implementation.registerBlockEntityRenderer(CobblemonBlockEntities.GILDED_CHEST, ::GildedChestBlockRenderer)
        this.implementation.registerBlockEntityRenderer(CobblemonBlockEntities.DISPLAY_CASE, ::DisplayCaseRenderer)
        this.implementation.registerBlockEntityRenderer(CobblemonBlockEntities.LECTERN, ::LecternBlockEntityRenderer)
        this.implementation.registerBlockEntityRenderer(CobblemonBlockEntities.CAMPFIRE, ::CampfireBlockEntityRenderer)
        this.implementation.registerBlockEntityRenderer(CobblemonBlockEntities.POKE_SNACK, ::PokeSnackBlockEntityRenderer)
    }

    private fun registerEntityRenderers() {
        LOGGER.info("Registering Pokémon renderer")
        this.implementation.registerEntityRenderer(CobblemonEntities.POKEMON, ::PokemonRenderer)
        LOGGER.info("Registering PokéBall renderer")
        this.implementation.registerEntityRenderer(CobblemonEntities.EMPTY_POKEBALL, ::PokeBallRenderer)
        LOGGER.info("Registering Generic Bedrock renderer")
        this.implementation.registerEntityRenderer(CobblemonEntities.GENERIC_BEDROCK_ENTITY, ::GenericBedrockRenderer)
        LOGGER.info("Registering PokeRod Bobber renderer")
        this.implementation.registerEntityRenderer(CobblemonEntities.POKE_BOBBER) { ctx -> PokeBobberEntityRenderer(ctx) }
        LOGGER.info("Registering NPC renderer")
        this.implementation.registerEntityRenderer(CobblemonEntities.NPC, ::NPCRenderer)
    }

    private fun registerItemColors() {
        implementation.registerItemColors(cobblemonResource("aprijuice_colour"), AprijuiceItemColorProvider, *CobblemonItems.aprijuices.toTypedArray())
        implementation.registerItemColors(cobblemonResource("pokesnack_colour"), PokeSnackItemColorProvider, CobblemonItems.POKE_CAKE)
        implementation.registerItemColors(cobblemonResource("pokebait_colour"), PokeBaitItemColorProvider, CobblemonItems.POKE_BAIT)
        implementation.registerItemColors(cobblemonResource("ponigiri_colour"), PonigiriItemColorProvider, CobblemonItems.PONIGIRI)
        implementation.registerItemColors(cobblemonResource("sinister_tea_colour"), SinisterTeaItemColorProvider, CobblemonItems.SINISTER_TEA)
    }

    fun reloadCodedAssets(resourceManager: ResourceManager) {
        LOGGER.info("Loading assets...")
        // Particles come first because animations need them.
        BedrockParticleOptionsRepository.loadEffects(resourceManager)
        // Animations come next because models need them.
        BedrockAnimationRepository.loadAnimations(
            resourceManager = resourceManager,
            directories = VaryingModelRepository.animationDirectories
        )
        VaryingModelRepository.reload(resourceManager)

        BerryModelRepository.reload(resourceManager)
        MiscModelRepository.reload(resourceManager)
        if (Cobblemon.implementation.modAPI != ModAPI.NEOFORGE) {
            CobblemonBakingOverrides.reload(resourceManager)
        }
        LOGGER.info("Loaded assets")
    }

    fun endBattle() {
        battle = null
        battleOverlay.lastKnownBattle = null
        BattleMusicController.endMusic()
    }
}
