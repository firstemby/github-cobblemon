/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.integration.controlify

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.OrientationControllable
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.CobblemonClient.ridingTurnInputHandler
import com.cobblemon.mod.common.client.gui.battle.BattleGUI
import com.cobblemon.mod.common.client.gui.behaviour.BehaviourEditorScreen
import com.cobblemon.mod.common.client.gui.config.CobblemonConfigScreen
import com.cobblemon.mod.common.client.gui.cookingpot.CookingPotScreen
import com.cobblemon.mod.common.client.gui.debug.riding.RidingStatsDebugGUI
import com.cobblemon.mod.common.client.gui.dialogue.DialogueScreen
import com.cobblemon.mod.common.client.gui.interact.battleRequest.BattleConfigureGUI
import com.cobblemon.mod.common.client.gui.interact.moveselect.MoveSelectGUI
import com.cobblemon.mod.common.client.gui.interact.partyselect.PartySelectGUI
import com.cobblemon.mod.common.client.gui.interact.wheel.InteractWheelGUI
import com.cobblemon.mod.common.client.gui.npc.NPCEditorScreen
import com.cobblemon.mod.common.client.gui.pc.PCGUI
import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUI
import com.cobblemon.mod.common.client.gui.pokenav.PokeNav
import com.cobblemon.mod.common.client.gui.snapshots.SnapshotWarningScreen
import com.cobblemon.mod.common.client.gui.startselection.StarterSelectionScreen
import com.cobblemon.mod.common.client.gui.summary.Summary
import com.cobblemon.mod.common.client.gui.trade.TradeGUI
import com.cobblemon.mod.common.client.integration.controlify.gui.CobblemonGUIProcessor
import com.cobblemon.mod.common.client.integration.controlify.gui.InteractWheelProcessor
import com.cobblemon.mod.common.client.keybind.CobblemonKeyBinds
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.cobblemonResource
import dev.isxander.controlify.api.ControlifyApi
import dev.isxander.controlify.api.bind.ControlifyBindApi
import dev.isxander.controlify.api.bind.InputBindingBuilder
import dev.isxander.controlify.api.bind.InputBindingSupplier
import dev.isxander.controlify.api.entrypoint.ControlifyEntrypoint
import dev.isxander.controlify.api.entrypoint.InitContext
import dev.isxander.controlify.api.entrypoint.PreInitContext
import dev.isxander.controlify.bindings.BindContext
import dev.isxander.controlify.bindings.ControlifyBindings
import dev.isxander.controlify.bindings.RadialIcons
import dev.isxander.controlify.screenop.ScreenProcessorFactory
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Items
import org.joml.Vector2d
import kotlin.jvm.optionals.getOrNull
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

class CobblemonControlifyEntrypoint : ControlifyEntrypoint {
    override fun onControlifyInit(initContext: InitContext) {
        ridingTurnInputHandler.put(
            cobblemonResource("compat.controlify"),
            ({ localPlayer: LocalPlayer, xDelta: Double, yDelta: Double, deltaTime: Double ->
                val vec = Vector2d(xDelta, yDelta)
                val controller = initContext.controlify().currentController.getOrNull()
                val vehicle = localPlayer.vehicle
                if (vehicle is OrientationControllable && vehicle.orientationController.isActive() && initContext.controlify().currentInputMode().isController && controller != null){
                    val multiplier = deltaTime * 1500
                    val config = controller.input().getOrNull()?.config()?.config()
                    val vSensitivity = config?.vLookSensitivity ?: 0.9f
                    val hSensitivity = config?.hLookSensitivity ?: 1f

                    // Should never be null once bindings are set
                    var vAxis = (RIDING_DOWN!!.on(controller).analogueNow() - RIDING_UP!!.on(controller).analogueNow()).toDouble()
                    var hAxis = (RIDING_RIGHT!!.on(controller).analogueNow() - RIDING_LEFT!!.on(controller).analogueNow()).toDouble()

                    vAxis *= multiplier * vSensitivity
                    hAxis *= multiplier * hSensitivity
                    vec.x = hAxis
                    vec.y = vAxis
                }
                vec
            })
        )

        CobblemonClient.ridingSprintHandler.put(
            cobblemonResource("compat.controlify")
        ) {
            initContext.controlify().currentController.getOrNull()?.let { ControlifyBindings.SPRINT.on(it).digitalNow() } == true
        }
    }

    override fun onControlifyPreInit(initContext: PreInitContext) {
        val bindings = ControlifyBindApi.get()
        bindings.registerBindContext(ORIENTATION_CONTROLLER_ACTIVE)

        RIDING_UP =
            bindings.registerBinding { builder: InputBindingBuilder ->
                builder
                    .id(cobblemonResource("riding_up"))
                    .category(Component.translatable("controlify.category.cobblemon.cobblemon"))
                    .name(Component.translatable("controlify.bind.cobblemon.riding_up"))
                    .allowedContexts(ORIENTATION_CONTROLLER_ACTIVE, BindContext.IN_GAME)
            }

        RIDING_DOWN =
            bindings.registerBinding { builder: InputBindingBuilder ->
                builder
                    .id(cobblemonResource("riding_down"))
                    .category(Component.translatable("controlify.category.cobblemon.cobblemon"))
                    .name(Component.translatable("controlify.bind.cobblemon.riding_down"))
                    .allowedContexts(ORIENTATION_CONTROLLER_ACTIVE, BindContext.IN_GAME)
            }

        RIDING_LEFT =
            bindings.registerBinding { builder: InputBindingBuilder ->
                builder
                    .id(cobblemonResource("riding_left"))
                    .category(Component.translatable("controlify.category.cobblemon.cobblemon"))
                    .name(Component.translatable("controlify.bind.cobblemon.riding_left"))
                    .allowedContexts(ORIENTATION_CONTROLLER_ACTIVE, BindContext.IN_GAME)
            }

        RIDING_RIGHT =
            bindings.registerBinding { builder: InputBindingBuilder ->
                builder
                    .id(cobblemonResource("riding_right"))
                    .category(Component.translatable("controlify.category.cobblemon.cobblemon"))
                    .name(Component.translatable("controlify.bind.cobblemon.riding_right"))
                    .allowedContexts(ORIENTATION_CONTROLLER_ACTIVE, BindContext.IN_GAME)
            }

        HIDE_PARTY =
            bindings.registerBinding { builder: InputBindingBuilder ->
                builder
                    .id(cobblemonResource("hide_party"))
                    .category(Component.translatable("controlify.category.cobblemon.cobblemon"))
                    .name(Component.translatable("key.cobblemon.hideparty"))
                    .allowedContexts(BindContext.IN_GAME)
                    .radialCandidate(RadialIcons.getItem(Items.BARRIER))
                    .addKeyCorrelation(CobblemonKeyBinds.HIDE_PARTY)
                    .keyEmulation(CobblemonKeyBinds.HIDE_PARTY)
            }

        SUMMARY =
            bindings.registerBinding { builder: InputBindingBuilder ->
                builder
                    .id(cobblemonResource("summary"))
                    .category(Component.translatable("controlify.category.cobblemon.cobblemon"))
                    .name(Component.translatable("key.cobblemon.summary"))
                    .allowedContexts(BindContext.IN_GAME)
                    .radialCandidate(RadialIcons.getItem(CobblemonItems.GREAT_BALL))
                    .addKeyCorrelation(CobblemonKeyBinds.SUMMARY)
                    .keyEmulation(CobblemonKeyBinds.SUMMARY)
            }

        PARTY_OVERLAY_DOWN =
            bindings.registerBinding { builder: InputBindingBuilder ->
                builder
                    .id(cobblemonResource("party_overlay_down"))
                    .category(Component.translatable("controlify.category.cobblemon.cobblemon"))
                    .name(Component.translatable("key.cobblemon.downshiftparty"))
                    .allowedContexts(HAS_POKEMON, BindContext.IN_GAME)
                    .radialCandidate(RadialIcons.EMPTY)
                    .addKeyCorrelation(CobblemonKeyBinds.PARTY_OVERLAY_DOWN)
                    .keyEmulation(CobblemonKeyBinds.PARTY_OVERLAY_DOWN)
            }

        PARTY_OVERLAY_UP =
            bindings.registerBinding { builder: InputBindingBuilder ->
                builder
                    .id(cobblemonResource("party_overlay_up"))
                    .category(Component.translatable("controlify.category.cobblemon.cobblemon"))
                    .name(Component.translatable("key.cobblemon.upshiftparty"))
                    .allowedContexts(HAS_POKEMON, BindContext.IN_GAME)
                    .radialCandidate(RadialIcons.EMPTY)
                    .addKeyCorrelation(CobblemonKeyBinds.PARTY_OVERLAY_UP)
                    .keyEmulation(CobblemonKeyBinds.PARTY_OVERLAY_DOWN)
            }

        SEND_OUT_POKEMON =
            bindings.registerBinding { builder: InputBindingBuilder ->
                builder
                    .id(cobblemonResource("send_out_pokemon"))
                    .category(Component.translatable("controlify.category.cobblemon.cobblemon"))
                    .name(Component.translatable("key.cobblemon.throwpartypokemon"))
                    .allowedContexts(HAS_POKEMON, BindContext.IN_GAME)
                    .radialCandidate(RadialIcons.getItem(CobblemonItems.POKE_BALL))
                    .addKeyCorrelation(CobblemonKeyBinds.SEND_OUT_POKEMON)
                    .keyEmulation(CobblemonKeyBinds.SEND_OUT_POKEMON)
            }


        RIDING_FREELOOK =
            bindings.registerBinding { builder: InputBindingBuilder ->
                builder
                    .id(cobblemonResource("riding_freelook"))
                    .category(Component.translatable("controlify.category.cobblemon.cobblemon"))
                    .name(Component.translatable("key.cobblemon.ridingfreelook"))
                    .allowedContexts(RIDING_POKEMON, BindContext.IN_GAME)
                    .radialCandidate(RadialIcons.EMPTY)
                    .addKeyCorrelation(CobblemonKeyBinds.RIDING_FREELOOK)
                    .keyEmulation(CobblemonKeyBinds.RIDING_FREELOOK)
            }

        INTERACT_WHEEL_UP_AXIS =
            bindings.registerBinding { builder: InputBindingBuilder ->
                builder
                    .id(cobblemonResource("interact_wheel_up"))
                    .category(Component.translatable("controlify.category.cobblemon.cobblemon"))
                    .name(Component.translatable("controlify.bind.cobblemon.interact_wheel_up"))
                    .allowedContexts(INTERACT_WHEEL_SCREEN, BindContext.IN_GAME)
            }

        INTERACT_WHEEL_DOWN_AXIS =
            bindings.registerBinding { builder: InputBindingBuilder ->
                builder
                    .id(cobblemonResource("interact_wheel_down"))
                    .category(Component.translatable("controlify.category.cobblemon.cobblemon"))
                    .name(Component.translatable("controlify.bind.cobblemon.interact_wheel_down"))
                    .allowedContexts(INTERACT_WHEEL_SCREEN, BindContext.IN_GAME)
            }

        INTERACT_WHEEL_RIGHT_AXIS =
            bindings.registerBinding { builder: InputBindingBuilder ->
                builder
                    .id(cobblemonResource("interact_wheel_right"))
                    .category(Component.translatable("controlify.category.cobblemon.cobblemon"))
                    .name(Component.translatable("controlify.bind.cobblemon.interact_wheel_right"))
                    .allowedContexts(INTERACT_WHEEL_SCREEN, BindContext.IN_GAME)
            }

        INTERACT_WHEEL_LEFT_AXIS =
            bindings.registerBinding { builder: InputBindingBuilder ->
                builder
                    .id(cobblemonResource("interact_wheel_left"))
                    .category(Component.translatable("controlify.category.cobblemon.cobblemon"))
                    .name(Component.translatable("controlify.bind.cobblemon.interact_wheel_left"))
                    .allowedContexts(INTERACT_WHEEL_SCREEN, BindContext.IN_GAME)
            }

        GUI_SECONDARY_NEXT_TAB =
            bindings.registerBinding { builder: InputBindingBuilder ->
                builder
                    .id(cobblemonResource("gui_secondary_next_tab"))
                    .category(Component.translatable("controlify.category.cobblemon.cobblemon"))
                    .name(Component.translatable("key.cobblemon.guisecondarynexttab"))
                    .allowedContexts(BindContext.ANY_SCREEN)
            }

        GUI_SECONDARY_PREV_TAB =
            bindings.registerBinding { builder: InputBindingBuilder ->
                builder
                    .id(cobblemonResource("gui_secondary_prev_tab"))
                    .category(Component.translatable("controlify.category.cobblemon.cobblemon"))
                    .name(Component.translatable("key.cobblemon.guisecondaryprevtab"))
                    .allowedContexts(BindContext.ANY_SCREEN)
            }

        GUI_START =
            bindings.registerBinding { builder: InputBindingBuilder ->
                builder
                    .id(cobblemonResource("gui_start"))
                    .category(Component.translatable("controlify.category.cobblemon.cobblemon"))
                    .name(Component.translatable("key.cobblemon.guistart"))
                    .allowedContexts(BindContext.ANY_SCREEN)
            }


        addProcessor(InteractWheelGUI::class, InteractWheelProcessor::class)
        // This is the lazy way to do all of these (just using the vmouse), if someone with more time and dedication did this that would be better
        addProcessor(BehaviourEditorScreen::class, CobblemonGUIProcessor::class)
        addProcessor(CobblemonConfigScreen::class, CobblemonGUIProcessor::class)
        addProcessor(RidingStatsDebugGUI::class, CobblemonGUIProcessor::class)
        addProcessor(DialogueScreen::class, CobblemonGUIProcessor::class)
        addProcessor(BattleConfigureGUI::class, CobblemonGUIProcessor::class)
        addProcessor(MoveSelectGUI::class, CobblemonGUIProcessor::class)
        addProcessor(PartySelectGUI::class, CobblemonGUIProcessor::class)
        addProcessor(NPCEditorScreen::class, CobblemonGUIProcessor::class)
        addProcessor(PCGUI::class, CobblemonGUIProcessor::class)
        addProcessor(SnapshotWarningScreen::class, CobblemonGUIProcessor::class)
        addProcessor(StarterSelectionScreen::class, CobblemonGUIProcessor::class)
        addProcessor(TradeGUI::class, CobblemonGUIProcessor::class)
        // Below are implemented processors for our GUIs
        addProcessor(Summary::class, CobblemonGUIProcessor::class)
        addProcessor(BattleGUI::class, CobblemonGUIProcessor::class)
        addProcessor(PokedexGUI::class, CobblemonGUIProcessor::class)
        // This once don't need any
        addProcessor(CookingPotScreen::class, CobblemonGUIProcessor::class)
    }

    override fun onControllersDiscovered(controlifyApi: ControlifyApi) {
    }

    // This is jank, but I did it after I got my wisdom teeth extracted so tbh I think I did good enough, but I won't be offended if someone changes it to something sane.
    fun addProcessor(t: KClass<out Screen>, processorClazz: KClass<out CobblemonGUIProcessor>) {
        ScreenProcessorFactory.registerProvider(t.java) {
            processorClazz.primaryConstructor?.call(it)
        }
    }

    companion object {
        var RIDING_UP: InputBindingSupplier? = null
        var RIDING_DOWN: InputBindingSupplier? = null
        var RIDING_RIGHT: InputBindingSupplier? = null
        var RIDING_LEFT: InputBindingSupplier? = null
        var HIDE_PARTY: InputBindingSupplier? = null
        var SUMMARY: InputBindingSupplier? = null
        var PARTY_OVERLAY_DOWN: InputBindingSupplier? = null
        var PARTY_OVERLAY_UP: InputBindingSupplier? = null
        var SEND_OUT_POKEMON: InputBindingSupplier? = null
        var RIDING_FREELOOK: InputBindingSupplier? = null
        var INTERACT_WHEEL_UP_AXIS: InputBindingSupplier? = null
        var INTERACT_WHEEL_DOWN_AXIS: InputBindingSupplier? = null
        var INTERACT_WHEEL_LEFT_AXIS: InputBindingSupplier? = null
        var INTERACT_WHEEL_RIGHT_AXIS: InputBindingSupplier? = null
        //gui special keybinds
        var GUI_SECONDARY_NEXT_TAB: InputBindingSupplier? = null
        var GUI_SECONDARY_PREV_TAB: InputBindingSupplier? = null
        var GUI_START: InputBindingSupplier? = null

        val RIDING_POKEMON: BindContext = BindContext(
            cobblemonResource("riding"),
            { mc: Minecraft ->
                mc.player?.vehicle is PokemonEntity
            }
        )

        val ORIENTATION_CONTROLLER_ACTIVE: BindContext = BindContext(
            cobblemonResource("orientation_controller_active"),
            { mc: Minecraft ->
                val localPlayer = mc.player
                if (localPlayer == null) return@BindContext false
                val vehicle = localPlayer.vehicle
                vehicle is OrientationControllable && vehicle.getOrientationController().isActive()
            }
        )

        val HAS_POKEMON: BindContext = BindContext(
            cobblemonResource("has_pokemon"),
            {mc: Minecraft ->
                !CobblemonClient.storage.party.isEmpty()
            }
        )

        val INTERACT_WHEEL_SCREEN: BindContext = BindContext(
            cobblemonResource("wheel_screen"),
            { it.screen is InteractWheelGUI}
        )
    }
}