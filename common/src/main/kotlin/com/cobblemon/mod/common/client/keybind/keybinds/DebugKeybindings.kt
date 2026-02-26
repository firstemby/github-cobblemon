/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.keybind.keybinds

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.camera.debug.CameraTrackDebugRenderer
import com.cobblemon.mod.common.client.debug.ChunkBoundaryDebugRenderer
import com.cobblemon.mod.common.client.gui.debug.camera.CameraFeedDebugScreen
import com.cobblemon.mod.common.client.gui.debug.riding.RidingStatsDebugGUI
import com.cobblemon.mod.common.client.keybind.CobblemonKeyBinding
import com.cobblemon.mod.common.client.keybind.KeybindCategories
import com.cobblemon.mod.common.client.render.camera.CameraFeedHandler
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.debug.RequestOpenRidingStatsDebugGUIPacket
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.client.gui.screens.Screen

object DebugKeybindings {
    val keybindings = listOf(
        ScaleUpKeybinding(),
        ScaleDownKeybinding(),
        TranslateLeftKeybinding(),
        TranslateRightKeybinding(),
        TranslateUpKeybinding(),
        TranslateDownKeybinding(),
        PrintModelSettingsKeybinding(),
        ToggleRidingStatsDebugGUIKeybinding(),
        ToggleChunkBoundaryDebugKeybinding(),
        ToggleCinematicFeedDebugKeybinding(),
        ToggleCameraTrackDebugKeybinding()
    )

    class ScaleUpKeybinding: CobblemonKeyBinding(
        "key.cobblemon.scaleportraitup",
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_EQUALS,
        KeybindCategories.COBBLEMON_DEBUG_CATEGORY
    ) {
        override fun onPress() {
            val currentlySelectedPokemon = CobblemonClient.storage.party.get(CobblemonClient.storage.selectedSlot)
            if (currentlySelectedPokemon != null) {
                val state = FloatingState().also { it.currentAspects = currentlySelectedPokemon.aspects }
                val model = VaryingModelRepository.getPoser(currentlySelectedPokemon.species.resourceIdentifier, state)
                model.portraitScale += 0.01F
            }

        }
    }
    class ScaleDownKeybinding: CobblemonKeyBinding(
        "key.cobblemon.scaleportraitdown",
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_MINUS,
        KeybindCategories.COBBLEMON_DEBUG_CATEGORY
    ) {
        override fun onPress() {
            val currentlySelectedPokemon = CobblemonClient.storage.party.get(CobblemonClient.storage.selectedSlot)
            if (currentlySelectedPokemon != null) {
                val state = FloatingState().also { it.currentAspects = currentlySelectedPokemon.aspects }
                val model = VaryingModelRepository.getPoser(currentlySelectedPokemon.species.resourceIdentifier, state)
                model.portraitScale -= 0.01F
            }

        }
    }

    class TranslateUpKeybinding: CobblemonKeyBinding(
        "key.cobblemon.translateportraitup",
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_I,
        KeybindCategories.COBBLEMON_DEBUG_CATEGORY
    ) {
        override fun onPress() {
            val currentlySelectedPokemon = CobblemonClient.storage.party.get(CobblemonClient.storage.selectedSlot)
            if (currentlySelectedPokemon != null) {
                val state = FloatingState().also { it.currentAspects = currentlySelectedPokemon.aspects }
                val model = VaryingModelRepository.getPoser(currentlySelectedPokemon.species.resourceIdentifier, state)
                model.portraitTranslation = model.portraitTranslation.add(0.0, -0.01, 0.0)
            }

        }
    }
    class TranslateDownKeybinding: CobblemonKeyBinding(
        "key.cobblemon.translateportraitdown",
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_K,
        KeybindCategories.COBBLEMON_DEBUG_CATEGORY
    ) {
        override fun onPress() {
            val currentlySelectedPokemon = CobblemonClient.storage.party.get(CobblemonClient.storage.selectedSlot)
            if (currentlySelectedPokemon != null) {
                val state = FloatingState().also { it.currentAspects = currentlySelectedPokemon.aspects }
                val model = VaryingModelRepository.getPoser(currentlySelectedPokemon.species.resourceIdentifier, state)
                model.portraitTranslation = model.portraitTranslation.add(0.0, 0.01, 0.0)
            }

        }
    }

    class TranslateLeftKeybinding: CobblemonKeyBinding(
        "key.cobblemon.translateportraitleft",
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_J,
        KeybindCategories.COBBLEMON_DEBUG_CATEGORY
    ) {
        override fun onPress() {
            val currentlySelectedPokemon = CobblemonClient.storage.party.get(CobblemonClient.storage.selectedSlot)
            if (currentlySelectedPokemon != null) {
                val state = FloatingState().also { it.currentAspects = currentlySelectedPokemon.aspects }
                val model = VaryingModelRepository.getPoser(currentlySelectedPokemon.species.resourceIdentifier, state)
                model.portraitTranslation = model.portraitTranslation.add(-0.01, 0.0, 0.0)
            }

        }
    }

    class TranslateRightKeybinding: CobblemonKeyBinding(
        "key.cobblemon.translateportraitright",
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_J,
        KeybindCategories.COBBLEMON_DEBUG_CATEGORY
    ) {
        override fun onPress() {
            val currentlySelectedPokemon = CobblemonClient.storage.party.get(CobblemonClient.storage.selectedSlot)
            if (currentlySelectedPokemon != null) {
                val state = FloatingState().also { it.currentAspects = currentlySelectedPokemon.aspects }
                val model = VaryingModelRepository.getPoser(currentlySelectedPokemon.species.resourceIdentifier, state)
                model.portraitTranslation = model.portraitTranslation.add(0.01, 0.0, 0.0)
            }
        }
    }

    class PrintModelSettingsKeybinding : CobblemonKeyBinding(
        "key.cobblemon.printmodelsettings",
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_PERIOD,
        KeybindCategories.COBBLEMON_DEBUG_CATEGORY
    ) {
        override fun onPress() {
            val currentlySelectedPokemon = CobblemonClient.storage.party.get(CobblemonClient.storage.selectedSlot)
            if (currentlySelectedPokemon != null) {
                val state = FloatingState().also { it.currentAspects = currentlySelectedPokemon.aspects }
                val model = VaryingModelRepository.getPoser(currentlySelectedPokemon.species.resourceIdentifier, state)
                Minecraft.getInstance().player?.sendSystemMessage(Component.literal("Portrait Translation: ${model.portraitTranslation}"))
                Minecraft.getInstance().player?.sendSystemMessage(Component.literal("Portrait Scale: ${model.portraitScale}"))
                Cobblemon.LOGGER.info("override var portraitTranslation = Vec3d(${model.portraitTranslation.x}, ${model.portraitTranslation.y}, ${model.portraitTranslation.z})")
                Cobblemon.LOGGER.info("override var portraitScale = ${model.portraitScale}F")
            }
        }
    }

    class ToggleRidingStatsDebugGUIKeybinding : CobblemonKeyBinding(
        "key.cobblemon.toggleridingstatsgui",
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_SEMICOLON,
        KeybindCategories.COBBLEMON_DEBUG_CATEGORY
    ) {
        override fun onPress() {
            val vehicle = Minecraft.getInstance().player?.vehicle ?: return
            if (vehicle is PokemonEntity) {
                RequestOpenRidingStatsDebugGUIPacket().sendToServer()
            }
        }
    }

    class ToggleChunkBoundaryDebugKeybinding : CobblemonKeyBinding(
        "key.cobblemon.togglechunkboundaries",
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_F7,
        KeybindCategories.COBBLEMON_DEBUG_CATEGORY
    ) {
        override fun onPress() {
            val newValue = ChunkBoundaryDebugRenderer.toggle()
            val tracked = ChunkBoundaryDebugRenderer.chunkCount()
            Minecraft.getInstance().player?.displayClientMessage(
                Component.literal(
                    buildString {
                        append("Chunk boundary debug ${if (newValue) "enabled" else "disabled"}")
                        if (newValue) {
                            append(" (tracking $tracked chunk")
                            if (tracked != 1) append('s')
                            append(')')
                        }
                    }
                ),
                true
            )
        }
    }

    class ToggleCinematicFeedDebugKeybinding : CobblemonKeyBinding(
        "key.cobblemon.togglecinematicfeeddebug",
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_F8,
        KeybindCategories.COBBLEMON_DEBUG_CATEGORY
    ) {
        override fun onPress() {
            val minecraft = Minecraft.getInstance()
            if (Screen.hasShiftDown()) {
                val newValue = !CameraFeedHandler.isDebugOverlayEnabled()
                CameraFeedHandler.setDebugOverlayEnabled(newValue)
                minecraft.player?.displayClientMessage(
                    Component.literal("Cinematic feed debug ${if (newValue) "enabled" else "disabled"}"),
                    true
                )
                return
            }

            if (minecraft.screen is CameraFeedDebugScreen) {
                minecraft.setScreen(null)
                return
            }

            minecraft.setScreen(CameraFeedDebugScreen())
            if (CameraFeedHandler.getActiveCameraIds().isEmpty()) {
                minecraft.player?.displayClientMessage(
                    Component.literal("Opened camera feed viewer (waiting for active feeds)"),
                    true
                )
            } else {
                minecraft.player?.displayClientMessage(
                    Component.literal("Opened camera feed viewer"),
                    true
                )
            }
        }
    }

    class ToggleCameraTrackDebugKeybinding : CobblemonKeyBinding(
        "key.cobblemon.togglecameratrackdebug",
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_F9,
        KeybindCategories.COBBLEMON_DEBUG_CATEGORY
    ) {
        override fun onPress() {
            val newValue = CameraTrackDebugRenderer.toggle()
            Minecraft.getInstance().player?.displayClientMessage(
                Component.literal("Camera track debug ${if (newValue) "enabled" else "disabled"}"),
                true
            )
        }
    }
}
