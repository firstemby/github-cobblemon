/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.pokemon

import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.cobblemon.mod.common.client.render.models.blockbench.PosableRenderStateBridge
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import org.joml.Quaternionf

class PokemonRenderState : LivingEntityRenderState(), PosableRenderStateBridge {
    var pokemonEntity: PokemonEntity? = null
    var guiPreviewEntity: PokemonEntity? = null
    var poseState: PosableState? = null
    var species: Identifier? = null
    var aspects: Set<String> = emptySet()
    var texture: Identifier = Identifier.parse("minecraft:textures/misc/white.png")
    var baseScale: Float = 1F
    var modelScale: Float = 1F
    var shadowSize: Float = 0.5F
    var poseType: PoseType = PoseType.PROFILE
    var applyPortraitTransform: Boolean = false
    var applyProfileTransform: Boolean = true
    var applyBaseScale: Boolean = false
    var doQuirks: Boolean = false
    var heldItem: ItemStack = ItemStack.EMPTY
    var previewRotation: Quaternionf? = null
    var previewHeadYaw: Float = 0F
    var previewHeadPitch: Float = 0F

    override val renderEntity: PokemonEntity?
        get() = pokemonEntity

    override val renderPosableState: PosableState?
        get() = poseState
}
