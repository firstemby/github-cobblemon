/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.progress

import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.pokemon.transformation.progress.TransformationProgress
import com.cobblemon.mod.common.api.pokemon.transformation.progress.TransformationProgressType
import com.cobblemon.mod.common.api.pokemon.transformation.progress.TransformationProgressTypes
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.form.PermanentForm
import com.cobblemon.mod.common.pokemon.transformation.requirements.UseMoveRequirement
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.resources.ResourceLocation
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * A [TransformationProgress] meant to keep track of the amount of times a specific move in battle.
 *
 * @author Licious
 * @since January 28th, 2022
 */
class UseMoveTransformationProgress : TransformationProgress<UseMoveTransformationProgress.Progress> {

    private var progress = Progress(MoveTemplate.dummy(""), 0)

    override fun id(): ResourceLocation = ID

    override fun currentProgress(): Progress = this.progress

    override fun updateProgress(progress: Progress) {
        this.progress = progress
    }

    override fun reset() {
        this.progress = Progress(MoveTemplate.dummy(""), 0)
    }

    override fun shouldKeep(pokemon: Pokemon): Boolean = supports(pokemon, this.progress.move)

    override fun type(): TransformationProgressType<*> = TransformationProgressTypes.USE_MOVE

    data class Progress(
        val move: MoveTemplate,
        val amount: Int
    )

    companion object {

        val ID = cobblemonResource("use_move")
        private const val MOVE = "move"
        private const val AMOUNT = "amount"

        @JvmStatic
        val CODEC: MapCodec<UseMoveTransformationProgress> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                MoveTemplate.BY_STRING_CODEC.fieldOf(MOVE).forGetter { it.progress.move },
                Codec.intRange(0, Int.MAX_VALUE).fieldOf(AMOUNT).forGetter { it.progress.amount }
            ).apply(instance) { move, amount -> UseMoveTransformationProgress().apply { updateProgress(Progress(move, amount)) } }
        }

        fun supports(pokemon: Pokemon, move: MoveTemplate): Boolean {
            val form = pokemon.form
            if (form !is PermanentForm) return false
            return form.transformations.any { transformation ->
                transformation.requirements.any { requirement ->
                    requirement is UseMoveRequirement && requirement.move == move
                }
            }
        }

    }

}