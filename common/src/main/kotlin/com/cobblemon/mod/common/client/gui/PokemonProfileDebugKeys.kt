/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3

fun handleProfileDebugKeys(keyCode: Int, poserResourceLocation: ResourceLocation, state: FloatingState) {
    if (!Cobblemon.config.enableDebugKeys) {
        return
    }
    val model = VaryingModelRepository.getPoser(poserResourceLocation, state)

    val delta = when (keyCode) {
        InputConstants.KEY_UP -> Vec3(0.0, -0.01, 0.0)
        InputConstants.KEY_DOWN -> Vec3(0.0, 0.01, 0.0)
        InputConstants.KEY_LEFT -> Vec3(-0.01, 0.0, 0.0)
        InputConstants.KEY_RIGHT -> Vec3(0.01, 0.0, 0.0)
        else -> null
    }

    if (delta != null) {
        model.profileTranslation = model.profileTranslation.add(delta)
        model.profileSummaryTranslation = model.profileSummaryTranslation?.add(delta)
        model.profilePokedexTranslation = model.profilePokedexTranslation?.add(delta)
    }

    val scaleDelta = when (keyCode) {
        InputConstants.KEY_EQUALS -> 0.01F
        InputConstants.KEY_MINUS -> -0.01F
        else -> null
    }

    if (scaleDelta != null) {
        model.profileScale += scaleDelta
        model.profileSummaryScale = model.profileSummaryScale?.plus(scaleDelta)
        model.profilePokedexScale = model.profilePokedexScale?.plus(scaleDelta)
    }
}

fun logProfileTranslations(poserResourceLocation: ResourceLocation, state: FloatingState) {
    if (!Cobblemon.config.enableDebugKeys) {
        return
    }

    val model = VaryingModelRepository.getPoser(poserResourceLocation, state)
    val player = Minecraft.getInstance().player

    fun Double.f2() = "%.2f".format(this)
    fun Float.f2() = "%.2f".format(this)

    fun sendTranslation(name: String, translation: Vec3) {
        player?.sendSystemMessage(Component.literal("$name Translation: (${translation.x.f2()}, ${translation.y.f2()}, ${translation.z.f2()})"))
    }

    fun sendScale(name: String, scale: Float) {
        player?.sendSystemMessage(Component.literal("$name Scale: ${scale.f2()}"))
    }

    fun logTranslation(name: String, translation: Vec3) {
        Cobblemon.LOGGER.info("override var ${name.replaceFirstChar(Char::lowercase)}Translation = Vec3(${translation.x.f2()}, ${translation.y.f2()}, ${translation.z.f2()})")
    }

    fun logScale(name: String, scale: Float) {
        Cobblemon.LOGGER.info("override var ${name.replaceFirstChar(Char::lowercase)}Scale = ${scale.f2()}F")
    }

    sendTranslation("Profile", model.profileTranslation)
    sendScale("Profile", model.profileScale)

    model.profileSummaryTranslation?.let { sendTranslation("Summary Profile", it) }
    model.profileSummaryScale?.let { sendScale("Summary Profile", it) }

    model.profilePokedexTranslation?.let { sendTranslation("Pokedex Profile", it) }
    model.profilePokedexScale?.let { sendScale("Pokedex Profile", it) }

    logTranslation("Profile", model.profileTranslation)
    logScale("Profile", model.profileScale)

    model.profileSummaryTranslation?.let { logTranslation("ProfileSummary", it) }
    model.profileSummaryScale?.let { logScale("ProfileSummary", it) }

    model.profilePokedexTranslation?.let { logTranslation("ProfilePokedex", it) }
    model.profilePokedexScale?.let { logScale("ProfilePokedex", it) }
}