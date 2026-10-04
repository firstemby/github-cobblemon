/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client

import com.cobblemon.mod.common.Cobblemon.LOGGER
import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.CobblemonClientImplementation
import com.cobblemon.mod.common.CobblemonEntities
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.CobblemonMenuType
import com.cobblemon.mod.common.api.berry.Berries
import com.cobblemon.mod.common.api.scheduling.ClientTaskTracker
import com.cobblemon.mod.common.api.storage.player.client.ClientGeneralPlayerData
import com.cobblemon.mod.common.api.storage.player.client.ClientPokedexManager
import com.cobblemon.mod.common.api.storage.player.client.ClientTMMoveManager
import com.cobblemon.mod.common.api.tags.CobblemonItemTags
import com.cobblemon.mod.common.block.entity.TintBlockEntity
import com.cobblemon.mod.common.block.sign.CobblemonSignBlock
import com.cobblemon.mod.common.client.battle.ClientBattle
import com.cobblemon.mod.common.client.gui.PartyOverlay
import com.cobblemon.mod.common.client.gui.PartyOverlayDataControl
import com.cobblemon.mod.common.client.gui.RideControlsOverlay
import com.cobblemon.mod.common.client.gui.battle.BattleOverlay
import com.cobblemon.mod.common.client.gui.cookingpot.CookingPotScreen
import com.cobblemon.mod.common.client.gui.party.PartyTutorialToasts
import com.cobblemon.mod.common.client.gui.tmmachine.TMMachineScreen
import com.cobblemon.mod.common.client.particle.BedrockParticleOptionsRepository
import com.cobblemon.mod.common.client.render.ClientPlayerIcon
import com.cobblemon.mod.common.client.render.DeferredRenderer
import com.cobblemon.mod.common.client.render.block.*
import com.cobblemon.mod.common.client.render.boat.CobblemonBoatRenderer
import com.cobblemon.mod.common.client.render.color.AprijuiceItemColorProvider
import com.cobblemon.mod.common.client.render.color.PokeBaitItemColorProvider
import com.cobblemon.mod.common.client.render.color.PokeSnackItemColorProvider
import com.cobblemon.mod.common.client.render.color.PonigiriItemColorProvider
import com.cobblemon.mod.common.client.render.color.SinisterTeaItemColorProvider
import com.cobblemon.mod.common.client.render.entity.PokeBobberEntityRenderer
import com.cobblemon.mod.common.client.render.generic.GenericBedrockRenderer
import com.cobblemon.mod.common.client.render.item.CobblemonBuiltinItemRendererRegistry
import com.cobblemon.mod.common.client.render.item.PokemonItemRenderer
import com.cobblemon.mod.common.client.render.layer.PlayerRecallBallRenderer
import com.cobblemon.mod.common.client.render.layer.PokemonOnShoulderRenderer
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockAnimationRepository
import com.cobblemon.mod.common.client.render.models.blockbench.repository.BerryModelRepository
import com.cobblemon.mod.common.client.render.models.blockbench.repository.MiscModelRepository
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.client.render.npc.NPCRenderer
import com.cobblemon.mod.common.client.render.pokeball.PokeBallRenderer
import com.cobblemon.mod.common.client.render.pokemon.PokemonRenderer
import com.cobblemon.mod.common.client.requests.ClientPlayerActionRequests
import com.cobblemon.mod.common.client.sound.BattleMusicController
import com.cobblemon.mod.common.client.sound.EntitySoundTracker
import com.cobblemon.mod.common.client.storage.ClientStorageManager
import com.cobblemon.mod.common.client.tooltips.AprijuiceTooltipGenerator
import com.cobblemon.mod.common.client.tooltips.CobblemonTooltipGenerator
import com.cobblemon.mod.common.client.tooltips.FishingBaitTooltipGenerator
import com.cobblemon.mod.common.client.tooltips.FishingRodTooltipGenerator
import com.cobblemon.mod.common.client.tooltips.PokePuffTooltipGenerator
import com.cobblemon.mod.common.client.tooltips.RecipeSeasoningAbsorptionTooltipGenerator
import com.cobblemon.mod.common.client.tooltips.SeasoningTooltipGenerator
import com.cobblemon.mod.common.client.tooltips.TechnicalMachineTooltipGenerator
import com.cobblemon.mod.common.client.tooltips.TooltipManager
import com.cobblemon.mod.common.client.trade.ClientTrade
import com.cobblemon.mod.common.entity.boat.CobblemonBoatType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.platform.events.PlatformEvents
import com.cobblemon.mod.common.pokedex.scanner.PokedexUsageContext
import com.cobblemon.mod.common.util.isLookingAt
import net.minecraft.client.Minecraft
import net.minecraft.client.color.block.BlockColor
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.MenuScreens
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.model.BoatModel
import net.minecraft.client.model.ChestBoatModel
import net.minecraft.client.model.PlayerModel
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.blockentity.HangingSignRenderer
import net.minecraft.client.renderer.blockentity.SignRenderer
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.resources.PlayerSkin
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB

object CobblemonClient {

    lateinit var implementation: CobblemonClientImplementation
    val storage = ClientStorageManager()
    var trade: ClientTrade? = null
    var battle: ClientBattle? = null
    var clientPlayerData = ClientGeneralPlayerData()
    var clientPokedexData = ClientPokedexManager(mutableMapOf())
    var clientTMMoveData = ClientTMMoveManager(mutableSetOf())

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
        clientTMMoveData = ClientTMMoveManager(mutableSetOf())
        storage.onLogin()
//        CobblemonDataProvider.canReload = false
    }

    fun onLogout() {
        storage.onLogout()
        battle = null
        battleOverlay.onLogout()
        ClientTaskTracker.clear()
        checkedStarterScreen = false
        overlay.resetAttachedToast()
        PartyTutorialToasts.reset()
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
        registerBlockRenderTypes()
        registerColors()
        registerFlywheelRenderers()
        this.registerEntityRenderers()
        this.registerItemColors()
        Berries.observable.subscribe {
            BerryModelRepository.patchModels()
        }
        this.registerTooltipManagers()
        this.registerMenuScreens()

        LOGGER.info("Registering custom BuiltinItemRenderers")
        CobblemonBuiltinItemRendererRegistry.register(CobblemonItems.POKEMON_MODEL, PokemonItemRenderer())

        PlatformEvents.CLIENT_ITEM_TOOLTIP.subscribe { event ->
            val stack = event.stack
            val lines = event.lines
            TooltipManager.generateTooltips(stack, lines, Screen.hasShiftDown())
        }

        PlatformEvents.CLIENT_ENTITY_UNLOAD.subscribe { event -> EntitySoundTracker.clear(event.entity.id) }
        PlatformEvents.CLIENT_TICK_POST.subscribe { event ->
            val player = event.client.player
            if (player != null) {
                val selectedItem = player.inventory.getItem(player.inventory.selected)
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

                nearbyPokemon?.forEach { entity ->
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
        TooltipManager.registerTooltipGenerator(TechnicalMachineTooltipGenerator)
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
        this.implementation.registerBlockColors(BlockColor { _, view, blockPos, _ ->
            blockPos?.let { pos ->
                view?.getBlockEntity(pos)?.let { blockEntity ->
                    if (blockEntity is TintBlockEntity) return@BlockColor blockEntity.getTint()
                }
            }
            return@BlockColor 0xFFFFFF
        }, CobblemonBlocks.POKE_SNACK,
            CobblemonBlocks.POKE_CAKE,
            CobblemonBlocks.TM_MACHINE
        )
    }

    private fun registerBlockRenderTypes() {

        this.implementation.registerBlockRenderType(
            RenderType.cutoutMipped(),
            CobblemonBlocks.APRICORN_LEAVES,
            CobblemonBlocks.SACCHARINE_LEAVES,
            CobblemonBlocks.POKE_CAKE
        )

        this.implementation.registerBlockRenderType(
            RenderType.translucent(),
            CobblemonBlocks.UPGRADE,
            CobblemonBlocks.DUBIOUS_DISC
        )

        this.implementation.registerBlockRenderType(
            RenderType.cutout(),
            CobblemonBlocks.GILDED_CHEST,
            CobblemonBlocks.FOSSIL_ANALYZER,
            CobblemonBlocks.APRICORN_DOOR,
            CobblemonBlocks.APRICORN_TRAPDOOR,
            CobblemonBlocks.APRICORN_SIGN,
            CobblemonBlocks.APRICORN_WALL_SIGN,
            CobblemonBlocks.APRICORN_HANGING_SIGN,
            CobblemonBlocks.APRICORN_WALL_HANGING_SIGN,
            CobblemonBlocks.BLACK_APRICORN_SAPLING,
            CobblemonBlocks.BLUE_APRICORN_SAPLING,
            CobblemonBlocks.GREEN_APRICORN_SAPLING,
            CobblemonBlocks.PINK_APRICORN_SAPLING,
            CobblemonBlocks.RED_APRICORN_SAPLING,
            CobblemonBlocks.WHITE_APRICORN_SAPLING,
            CobblemonBlocks.YELLOW_APRICORN_SAPLING,
            CobblemonBlocks.POTTED_BLACK_APRICORN_SAPLING,
            CobblemonBlocks.POTTED_BLUE_APRICORN_SAPLING,
            CobblemonBlocks.POTTED_GREEN_APRICORN_SAPLING,
            CobblemonBlocks.POTTED_PINK_APRICORN_SAPLING,
            CobblemonBlocks.POTTED_RED_APRICORN_SAPLING,
            CobblemonBlocks.POTTED_WHITE_APRICORN_SAPLING,
            CobblemonBlocks.POTTED_YELLOW_APRICORN_SAPLING,
            CobblemonBlocks.BLACK_APRICORN,
            CobblemonBlocks.BLUE_APRICORN,
            CobblemonBlocks.GREEN_APRICORN,
            CobblemonBlocks.PINK_APRICORN,
            CobblemonBlocks.RED_APRICORN,
            CobblemonBlocks.WHITE_APRICORN,
            CobblemonBlocks.YELLOW_APRICORN,
            CobblemonBlocks.HEALING_MACHINE,
            CobblemonBlocks.MEDICINAL_LEEK,
            CobblemonBlocks.HEALING_MACHINE,
            CobblemonBlocks.MONITOR,
            CobblemonBlocks.RED_MINT,
            CobblemonBlocks.BLUE_MINT,
            CobblemonBlocks.CYAN_MINT,
            CobblemonBlocks.PINK_MINT,
            CobblemonBlocks.GREEN_MINT,
            CobblemonBlocks.WHITE_MINT,
            CobblemonBlocks.PASTURE,
            CobblemonBlocks.ENERGY_ROOT,
            CobblemonBlocks.BIG_ROOT,
            CobblemonBlocks.REVIVAL_HERB,
            CobblemonBlocks.VIVICHOKE_SEEDS,
            CobblemonBlocks.HEARTY_GRAINS,
            CobblemonBlocks.PEP_UP_FLOWER,
            CobblemonBlocks.POTTED_PEP_UP_FLOWER,
            CobblemonBlocks.REVIVAL_HERB,
            *CobblemonBlocks.berries().values.toTypedArray(),
            CobblemonBlocks.GALARICA_NUT_BUSH,
            CobblemonBlocks.RESTORATION_TANK,
            CobblemonBlocks.SMALL_BUDDING_TUMBLESTONE,
            CobblemonBlocks.MEDIUM_BUDDING_TUMBLESTONE,
            CobblemonBlocks.LARGE_BUDDING_TUMBLESTONE,
            CobblemonBlocks.TUMBLESTONE_CLUSTER,
            CobblemonBlocks.SMALL_BUDDING_BLACK_TUMBLESTONE,
            CobblemonBlocks.MEDIUM_BUDDING_BLACK_TUMBLESTONE,
            CobblemonBlocks.LARGE_BUDDING_BLACK_TUMBLESTONE,
            CobblemonBlocks.BLACK_TUMBLESTONE_CLUSTER,
            CobblemonBlocks.TYPE_GEM_CLUSTER_NORMAL,
            CobblemonBlocks.TYPE_GEM_CLUSTER_FIRE,
            CobblemonBlocks.TYPE_GEM_CLUSTER_WATER,
            CobblemonBlocks.TYPE_GEM_CLUSTER_ELECTRIC,
            CobblemonBlocks.TYPE_GEM_CLUSTER_GRASS,
            CobblemonBlocks.TYPE_GEM_CLUSTER_ICE,
            CobblemonBlocks.TYPE_GEM_CLUSTER_FIGHTING,
            CobblemonBlocks.TYPE_GEM_CLUSTER_POISON,
            CobblemonBlocks.TYPE_GEM_CLUSTER_GROUND,
            CobblemonBlocks.TYPE_GEM_CLUSTER_FLYING,
            CobblemonBlocks.TYPE_GEM_CLUSTER_PSYCHIC,
            CobblemonBlocks.TYPE_GEM_CLUSTER_BUG,
            CobblemonBlocks.TYPE_GEM_CLUSTER_ROCK,
            CobblemonBlocks.TYPE_GEM_CLUSTER_GHOST,
            CobblemonBlocks.TYPE_GEM_CLUSTER_DRAGON,
            CobblemonBlocks.TYPE_GEM_CLUSTER_DARK,
            CobblemonBlocks.TYPE_GEM_CLUSTER_STEEL,
            CobblemonBlocks.TYPE_GEM_CLUSTER_FAIRY,
            CobblemonBlocks.SMALL_BUDDING_SKY_TUMBLESTONE,
            CobblemonBlocks.MEDIUM_BUDDING_SKY_TUMBLESTONE,
            CobblemonBlocks.LARGE_BUDDING_SKY_TUMBLESTONE,
            CobblemonBlocks.SKY_TUMBLESTONE_CLUSTER,
            CobblemonBlocks.GIMMIGHOUL_CHEST,
            CobblemonBlocks.DISPLAY_CASE,
            CobblemonBlocks.SACCHARINE_DOOR,
            CobblemonBlocks.SACCHARINE_TRAPDOOR,
            CobblemonBlocks.SACCHARINE_SIGN,
            CobblemonBlocks.SACCHARINE_WALL_SIGN,
            CobblemonBlocks.SACCHARINE_HANGING_SIGN,
            CobblemonBlocks.SACCHARINE_WALL_HANGING_SIGN,
            CobblemonBlocks.SACCHARINE_SAPLING,
            CobblemonBlocks.POTTED_SACCHARINE_SAPLING,
            CobblemonBlocks.POKE_SNACK,
            CobblemonBlocks.LECTERN,
            CobblemonBlocks.CAMPFIRE,
            CobblemonBlocks.SOUL_CAMPFIRE,
            CobblemonBlocks.BLACK_CAMPFIRE_POT,
            CobblemonBlocks.BLUE_CAMPFIRE_POT,
            CobblemonBlocks.GREEN_CAMPFIRE_POT,
            CobblemonBlocks.PINK_CAMPFIRE_POT,
            CobblemonBlocks.RED_CAMPFIRE_POT,
            CobblemonBlocks.WHITE_CAMPFIRE_POT,
            CobblemonBlocks.YELLOW_CAMPFIRE_POT,
            CobblemonBlocks.WHITE_PLAQUE,
            CobblemonBlocks.LIGHT_GRAY_PLAQUE,
            CobblemonBlocks.GRAY_PLAQUE,
            CobblemonBlocks.BLACK_PLAQUE,
            CobblemonBlocks.BROWN_PLAQUE,
            CobblemonBlocks.RED_PLAQUE,
            CobblemonBlocks.ORANGE_PLAQUE,
            CobblemonBlocks.YELLOW_PLAQUE,
            CobblemonBlocks.LIME_PLAQUE,
            CobblemonBlocks.GREEN_PLAQUE,
            CobblemonBlocks.CYAN_PLAQUE,
            CobblemonBlocks.LIGHT_BLUE_PLAQUE,
            CobblemonBlocks.BLUE_PLAQUE,
            CobblemonBlocks.PURPLE_PLAQUE,
            CobblemonBlocks.MAGENTA_PLAQUE,
            CobblemonBlocks.PINK_PLAQUE,
            CobblemonBlocks.BLUNDER_POLICY,
            CobblemonBlocks.WEAKNESS_POLICY,
            CobblemonBlocks.POTION,
            CobblemonBlocks.SUPER_POTION,
            CobblemonBlocks.HYPER_POTION,
            CobblemonBlocks.MAX_POTION,
            CobblemonBlocks.FULL_RESTORE,
            CobblemonBlocks.ANTIDOTE,
            CobblemonBlocks.AWAKENING,
            CobblemonBlocks.BURN_HEAL,
            CobblemonBlocks.ICE_HEAL,
            CobblemonBlocks.PARALYZE_HEAL,
            CobblemonBlocks.FULL_HEAL,
            CobblemonBlocks.ETHER,
            CobblemonBlocks.MAX_ETHER,
            CobblemonBlocks.ELIXIR,
            CobblemonBlocks.MAX_ELIXIR,
            CobblemonBlocks.DIRE_HIT,
            CobblemonBlocks.GUARD_SPEC,
            CobblemonBlocks.X_ACCURACY,
            CobblemonBlocks.X_ATTACK,
            CobblemonBlocks.X_DEFENSE,
            CobblemonBlocks.X_SP_ATK,
            CobblemonBlocks.X_SP_DEF,
            CobblemonBlocks.X_SPEED,
            CobblemonBlocks.TM_MACHINE,   

            CobblemonBlocks.GALARICA_CUFF,
            CobblemonBlocks.GALARICA_WREATH,
            CobblemonBlocks.CHIPPED_POT,
            CobblemonBlocks.CRACKED_POT,
            CobblemonBlocks.UNREMARKABLE_TEACUP,
            CobblemonBlocks.MASTERPIECE_TEACUP,
            CobblemonBlocks.LUMINOUS_MOSS,
            CobblemonBlocks.ICY_ROCK,
            CobblemonBlocks.HEAT_ROCK,
            CobblemonBlocks.SMOOTH_ROCK,
            CobblemonBlocks.DAMP_ROCK,
            CobblemonBlocks.DESTINY_KNOT,
            CobblemonBlocks.METRONOME,
            CobblemonBlocks.KINGS_ROCK,
            CobblemonBlocks.FLAME_ORB,
            CobblemonBlocks.LIFE_ORB,
            CobblemonBlocks.TOXIC_ORB,
            CobblemonBlocks.LIGHT_BALL,
            CobblemonBlocks.SMOKE_BALL,
            CobblemonBlocks.NEVER_MELT_ICE,
            CobblemonBlocks.WIDE_LENS,
            CobblemonBlocks.ZOOM_LENS,
            CobblemonBlocks.SCOPE_LENS,
            CobblemonBlocks.MOOMOO_MILK,
            CobblemonBlocks.MEDICINAL_BREW,
            CobblemonBlocks.TERRAIN_EXTENDER,
            
            CobblemonBlocks.RED_MINT_LEAF_LITTER,
            CobblemonBlocks.POTTED_RED_MINT,
            CobblemonBlocks.BLUE_MINT_LEAF_LITTER,
            CobblemonBlocks.POTTED_BLUE_MINT,
            CobblemonBlocks.CYAN_MINT_LEAF_LITTER,
            CobblemonBlocks.POTTED_CYAN_MINT,
            CobblemonBlocks.PINK_MINT_LEAF_LITTER,
            CobblemonBlocks.POTTED_PINK_MINT,
            CobblemonBlocks.GREEN_MINT_LEAF_LITTER,
            CobblemonBlocks.POTTED_GREEN_MINT,
            CobblemonBlocks.WHITE_MINT_LEAF_LITTER,
            CobblemonBlocks.POTTED_WHITE_MINT,

            CobblemonBlocks.LOBAILA,
            CobblemonBlocks.POMOHANA,
            CobblemonBlocks.PAUVILLEA,
            CobblemonBlocks.WISTENSU,
            CobblemonBlocks.WISTENSU_VINE,
            CobblemonBlocks.ELECTRIC_SEED,
            CobblemonBlocks.VOLTACONIA,
            CobblemonBlocks.GRASSY_SEED,
            CobblemonBlocks.VERDROOT,
            CobblemonBlocks.MISTY_SEED,
            CobblemonBlocks.MISTLEAF,
            CobblemonBlocks.PSYCHIC_SEED,
            CobblemonBlocks.LUCIBELL,

            CobblemonBlocks.POTTED_AGUAV_BERRY,
            CobblemonBlocks.POTTED_APICOT_BERRY,
            CobblemonBlocks.POTTED_ASPEAR_BERRY,
            CobblemonBlocks.POTTED_BABIRI_BERRY,
            CobblemonBlocks.POTTED_BELUE_BERRY,
            CobblemonBlocks.POTTED_BLUK_BERRY,
            CobblemonBlocks.POTTED_CHARTI_BERRY,
            CobblemonBlocks.POTTED_CHERI_BERRY,
            CobblemonBlocks.POTTED_CHESTO_BERRY,
            CobblemonBlocks.POTTED_CHILAN_BERRY,
            CobblemonBlocks.POTTED_CHOPLE_BERRY,
            CobblemonBlocks.POTTED_COBA_BERRY,
            CobblemonBlocks.POTTED_COLBUR_BERRY,
            CobblemonBlocks.POTTED_CORNN_BERRY,
            CobblemonBlocks.POTTED_CUSTAP_BERRY,
            CobblemonBlocks.POTTED_DURIN_BERRY,
            CobblemonBlocks.POTTED_EGGANT_BERRY,
            CobblemonBlocks.POTTED_ENIGMA_BERRY,
            CobblemonBlocks.POTTED_FIGY_BERRY,
            CobblemonBlocks.POTTED_GANLON_BERRY,
            CobblemonBlocks.POTTED_GREPA_BERRY,
            CobblemonBlocks.POTTED_HABAN_BERRY,
            CobblemonBlocks.POTTED_HONDEW_BERRY,
            CobblemonBlocks.POTTED_HOPO_BERRY,
            CobblemonBlocks.POTTED_IAPAPA_BERRY,
            CobblemonBlocks.POTTED_JABOCA_BERRY,
            CobblemonBlocks.POTTED_KASIB_BERRY,
            CobblemonBlocks.POTTED_KEBIA_BERRY,
            CobblemonBlocks.POTTED_KEE_BERRY,
            CobblemonBlocks.POTTED_KELPSY_BERRY,
            CobblemonBlocks.POTTED_LANSAT_BERRY,
            CobblemonBlocks.POTTED_LEPPA_BERRY,
            CobblemonBlocks.POTTED_LIECHI_BERRY,
            CobblemonBlocks.POTTED_LUM_BERRY,
            CobblemonBlocks.POTTED_MAGOST_BERRY,
            CobblemonBlocks.POTTED_MAGO_BERRY,
            CobblemonBlocks.POTTED_MARANGA_BERRY,
            CobblemonBlocks.POTTED_MICLE_BERRY,
            CobblemonBlocks.POTTED_NANAB_BERRY,
            CobblemonBlocks.POTTED_NOMEL_BERRY,
            CobblemonBlocks.POTTED_OCCA_BERRY,
            CobblemonBlocks.POTTED_ORAN_BERRY,
            CobblemonBlocks.POTTED_PAMTRE_BERRY,
            CobblemonBlocks.POTTED_PASSHO_BERRY,
            CobblemonBlocks.POTTED_PAYAPA_BERRY,
            CobblemonBlocks.POTTED_PECHA_BERRY,
            CobblemonBlocks.POTTED_PERSIM_BERRY,
            CobblemonBlocks.POTTED_PETAYA_BERRY,
            CobblemonBlocks.POTTED_PINAP_BERRY,
            CobblemonBlocks.POTTED_POMEG_BERRY,
            CobblemonBlocks.POTTED_QUALOT_BERRY,
            CobblemonBlocks.POTTED_RABUTA_BERRY,
            CobblemonBlocks.POTTED_RAWST_BERRY,
            CobblemonBlocks.POTTED_RAZZ_BERRY,
            CobblemonBlocks.POTTED_RINDO_BERRY,
            CobblemonBlocks.POTTED_ROSELI_BERRY,
            CobblemonBlocks.POTTED_ROWAP_BERRY,
            CobblemonBlocks.POTTED_SALAC_BERRY,
            CobblemonBlocks.POTTED_SHUCA_BERRY,
            CobblemonBlocks.POTTED_SITRUS_BERRY,
            CobblemonBlocks.POTTED_SPELON_BERRY,
            CobblemonBlocks.POTTED_STARF_BERRY,
            CobblemonBlocks.POTTED_TAMATO_BERRY,
            CobblemonBlocks.POTTED_TANGA_BERRY,
            CobblemonBlocks.POTTED_TOUGA_BERRY,
            CobblemonBlocks.POTTED_WACAN_BERRY,
            CobblemonBlocks.POTTED_WATMEL_BERRY,
            CobblemonBlocks.POTTED_WEPEAR_BERRY,
            CobblemonBlocks.POTTED_WIKI_BERRY,
            CobblemonBlocks.POTTED_YACHE_BERRY
        )

        this.createBoatModelLayers()
    }

    fun beforeChatRender(context: GuiGraphics, partialDeltaTicks: Float) {
        val partialDeltaTicks = Minecraft.getInstance().timer // Checking that this even works
//        ClientTaskTracker.update(partialDeltaTicks / 20f)
        if (battle == null) {
            overlay.render(context, partialDeltaTicks)
        } else {
            battleOverlay.render(context, partialDeltaTicks)
        }
        rideControlsOverlay.render(context, partialDeltaTicks)
    }

    @Suppress("UNCHECKED_CAST")
    fun onAddLayer(skinMap: Map<PlayerSkin.Model, EntityRenderer<out Player>>?) {
        var renderer: LivingEntityRenderer<Player, PlayerModel<Player>>? =
            skinMap?.get(PlayerSkin.Model.WIDE) as LivingEntityRenderer<Player, PlayerModel<Player>>
        renderer?.addLayer(PokemonOnShoulderRenderer(renderer))
        renderer?.addLayer(PlayerRecallBallRenderer(renderer))
        renderer = skinMap[PlayerSkin.Model.SLIM] as LivingEntityRenderer<Player, PlayerModel<Player>>?
        renderer?.addLayer(PokemonOnShoulderRenderer(renderer))
        renderer?.addLayer(PlayerRecallBallRenderer(renderer))
    }

    private fun registerMenuScreens() {
        MenuScreens.register(CobblemonMenuType.COOKING_POT, ::CookingPotScreen)
        MenuScreens.register(CobblemonMenuType.TM_MACHINE, ::TMMachineScreen)
    }

    private fun registerBlockEntityRenderers() {
        this.implementation.registerBlockEntityRenderer(
            CobblemonBlockEntities.HEALING_MACHINE,
            ::HealingMachineRenderer
        )
        this.implementation.registerBlockEntityRenderer(CobblemonBlockEntities.BERRY, ::BerryBlockRenderer)
        this.implementation.registerBlockEntityRenderer(CobblemonBlockEntities.SIGN, ::SignRenderer)
        this.implementation.registerBlockEntityRenderer(CobblemonBlockEntities.HANGING_SIGN, ::HangingSignRenderer)
        this.implementation.registerBlockEntityRenderer(
            CobblemonBlockEntities.FOSSIL_ANALYZER,
            ::FossilAnalyzerRenderer
        )
        this.implementation.registerBlockEntityRenderer(
            CobblemonBlockEntities.RESTORATION_TANK,
            ::RestorationTankRenderer
        )
        this.implementation.registerBlockEntityRenderer(CobblemonBlockEntities.HABITAT_BLOCK, ::HabitatBlockRenderer)
        this.implementation.registerBlockEntityRenderer(CobblemonBlockEntities.GILDED_CHEST, ::GildedChestBlockRenderer)
        this.implementation.registerBlockEntityRenderer(CobblemonBlockEntities.DISPLAY_CASE, ::DisplayCaseRenderer)
        this.implementation.registerBlockEntityRenderer(CobblemonBlockEntities.LECTERN, ::LecternBlockEntityRenderer)
        this.implementation.registerBlockEntityRenderer(CobblemonBlockEntities.CAMPFIRE, ::CampfireBlockEntityRenderer)
        this.implementation.registerBlockEntityRenderer(CobblemonBlockEntities.POKE_SNACK, ::PokeSnackBlockEntityRenderer)
        this.implementation.registerBlockEntityRenderer(CobblemonBlockEntities.DISC_SHELF, ::DiscShelfBlockEntityRenderer)
    }

    private fun registerEntityRenderers() {
        LOGGER.info("Registering Pokémon renderer")
        this.implementation.registerEntityRenderer(CobblemonEntities.POKEMON, ::PokemonRenderer)
        LOGGER.info("Registering PokéBall renderer")
        this.implementation.registerEntityRenderer(CobblemonEntities.EMPTY_POKEBALL, ::PokeBallRenderer)
        LOGGER.info("Registering Boat renderer")
        this.implementation.registerEntityRenderer(CobblemonEntities.BOAT) { ctx -> CobblemonBoatRenderer(ctx, false) }
        LOGGER.info("Registering Boat with Chest renderer")
        this.implementation.registerEntityRenderer(CobblemonEntities.CHEST_BOAT) { ctx -> CobblemonBoatRenderer(ctx, true) }
        LOGGER.info("Registering Generic Bedrock renderer")
        this.implementation.registerEntityRenderer(CobblemonEntities.GENERIC_BEDROCK_ENTITY, ::GenericBedrockRenderer)
        LOGGER.info("Registering Generic Bedrock Entity renderer")
        this.implementation.registerEntityRenderer(CobblemonEntities.GENERIC_BEDROCK_ENTITY, ::GenericBedrockRenderer)
        LOGGER.info("Registering PokeRod Bobber renderer")
        this.implementation.registerEntityRenderer(CobblemonEntities.POKE_BOBBER) { ctx -> PokeBobberEntityRenderer(ctx) }
        LOGGER.info("Registering NPC renderer")
        this.implementation.registerEntityRenderer(CobblemonEntities.NPC, ::NPCRenderer)
    }

    private fun registerItemColors() {
        implementation.registerItemColors(AprijuiceItemColorProvider, *CobblemonItems.aprijuices.toTypedArray())
        implementation.registerItemColors(PokeSnackItemColorProvider, CobblemonItems.POKE_SNACK, CobblemonItems.POKE_CAKE)
        implementation.registerItemColors(PokeBaitItemColorProvider, CobblemonItems.POKE_BAIT)
        implementation.registerItemColors(PonigiriItemColorProvider, CobblemonItems.PONIGIRI)
        implementation.registerItemColors(SinisterTeaItemColorProvider, CobblemonItems.SINISTER_TEA)
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
        LOGGER.info("Loaded assets")
    }

    fun endBattle() {
        battle = null
        battleOverlay.lastKnownBattle = null
        BattleMusicController.endMusic()
    }

    private fun createBoatModelLayers() {
        CobblemonBoatType.entries.forEach { type ->
            this.implementation.registerLayer(
                CobblemonBoatRenderer.createBoatModelLayer(type, false),
                BoatModel::createBodyModel
            )
            this.implementation.registerLayer(
                CobblemonBoatRenderer.createBoatModelLayer(type, true),
                ChestBoatModel::createBodyModel
            )
        }
    }
}
