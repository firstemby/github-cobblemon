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
import com.cobblemon.mod.common.client.CobblemonClient.ridingTurnInputHandler
import com.cobblemon.mod.common.client.gui.interact.wheel.InteractWheelGUI
import com.cobblemon.mod.common.client.integration.controlify.gui.InteractWheelProcessor
import com.cobblemon.mod.common.util.cobblemonResource
import dev.isxander.controlify.api.ControlifyApi
import dev.isxander.controlify.api.bind.ControlifyBindApi
import dev.isxander.controlify.api.bind.InputBindingBuilder
import dev.isxander.controlify.api.bind.InputBindingSupplier
import dev.isxander.controlify.api.entrypoint.ControlifyEntrypoint
import dev.isxander.controlify.api.entrypoint.InitContext
import dev.isxander.controlify.api.entrypoint.PreInitContext
import dev.isxander.controlify.bindings.BindContext
import dev.isxander.controlify.bindings.RadialIcons
import dev.isxander.controlify.screenop.ScreenProcessorFactory
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import org.joml.Vector2d
import java.util.function.Function
import kotlin.jvm.optionals.getOrNull

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
                    val vSensitivity = 1f // TODO: make config var
                    val hSensitivity = 1f // TODO: make config var

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
    }

    override fun onControlifyPreInit(initContext: PreInitContext) {
        val bindings = ControlifyBindApi.get()
        bindings.registerBindContext(RIDING_POKEMON)

        RIDING_UP =
            bindings.registerBinding { builder: InputBindingBuilder? ->
                builder!!
                    .id(cobblemonResource("riding_up"))
                    .category(Component.translatable("controlify.category.cobblemon.riding"))
                    .name(Component.translatable("controlify.bind.cobblemon.riding_up"))
                    .allowedContexts(RIDING_POKEMON, BindContext.IN_GAME)
            }

        RIDING_DOWN =
            bindings.registerBinding { builder: InputBindingBuilder? ->
                builder!!
                    .id(cobblemonResource("riding_down"))
                    .category(Component.translatable("controlify.category.cobblemon.riding"))
                    .name(Component.translatable("controlify.bind.cobblemon.riding_down"))
                    .allowedContexts(RIDING_POKEMON, BindContext.IN_GAME)
            }

        RIDING_LEFT =
            bindings.registerBinding { builder: InputBindingBuilder? ->
                builder!!
                    .id(cobblemonResource("riding_left"))
                    .category(Component.translatable("controlify.category.cobblemon.riding"))
                    .name(Component.translatable("controlify.bind.cobblemon.riding_left"))
                    .allowedContexts(RIDING_POKEMON, BindContext.IN_GAME)
            }

        RIDING_RIGHT =
            bindings.registerBinding { builder: InputBindingBuilder? ->
                builder!!
                    .id(cobblemonResource("riding_right"))
                    .category(Component.translatable("controlify.category.cobblemon.riding"))
                    .name(Component.translatable("controlify.bind.cobblemon.riding_right"))
                    .allowedContexts(RIDING_POKEMON, BindContext.IN_GAME)
            }
        HIDE_PARTY =
            bindings.registerBinding { builder: InputBindingBuilder? ->
                builder!!
                    .id(cobblemonResource("hide_party"))
                    .category(Component.translatable("controlify.category.cobblemon.cobblemon"))
                    .name(Component.translatable("controlify.bind.cobblemon.hide_party"))
                    .allowedContexts(BindContext.IN_GAME)
                    .radialCandidate(RadialIcons.getItem(CobblemonItems.POKE_BALL))
            }

        ScreenProcessorFactory.registerProvider(InteractWheelGUI::class.java, InteractWheelProcessor.InteractWheelFactory())
    }

    override fun onControllersDiscovered(controlifyApi: ControlifyApi?) {
    }

    companion object {
        var RIDING_UP: InputBindingSupplier? = null
        var RIDING_DOWN: InputBindingSupplier? = null
        var RIDING_RIGHT: InputBindingSupplier? = null
        var RIDING_LEFT: InputBindingSupplier? = null
        var HIDE_PARTY: InputBindingSupplier? = null
        var SUMMARY: InputBindingSupplier? = null
        var PARTY_OVERLAY_DOWN: InputBindingSupplier? = null
        var SEND_OUT_POKEMON: InputBindingSupplier? = null
        var RIDING_FREELOOK: InputBindingSupplier? = null

        val RIDING_POKEMON: BindContext = BindContext(
            cobblemonResource("riding"),
            Function { mc: Minecraft ->
                val localPlayer = mc.player
                if (localPlayer == null) return@Function false
                val vehicle = localPlayer.vehicle
                vehicle is OrientationControllable && vehicle.getOrientationController().isActive()
            }
        )
    }
}