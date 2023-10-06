/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.progress

import com.cobblemon.mod.common.api.pokemon.transformation.progress.TransformationProgress
import com.cobblemon.mod.common.api.pokemon.transformation.progress.TransformationProgressType
import com.cobblemon.mod.common.api.pokemon.transformation.progress.TransformationProgressTypes
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.requirements.DamageTakenRequirement
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.resources.ResourceLocation
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * A [TransformationProgress] meant to keep track of damage taken in battle without fainting.
 *
 * @author Licious
 * @since January 28th, 2022
 */
class DamageTakenTransformationProgress : TransformationProgress<DamageTakenTransformationProgress.Progress> {

    private var progress = Progress(0)

    override fun id(): ResourceLocation = ID

    override fun currentProgress(): Progress = this.progress

    override fun updateProgress(progress: Progress) {
        this.progress = progress
    }

    override fun reset() {
        this.updateProgress(Progress(0))
    }

    override fun shouldKeep(pokemon: Pokemon): Boolean = supports(pokemon)

    override fun type(): TransformationProgressType<*> = TransformationProgressTypes.DAMAGE_TAKEN

    data class Progress(val amount: Int)

    companion object {

        val ID = cobblemonResource(DamageTakenRequirement.ADAPTER_VARIANT)
        private const val AMOUNT = "amount"

        @JvmStatic
        val CODEC: MapCodec<DamageTakenTransformationProgress> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                Codec.intRange(0, Int.MAX_VALUE).fieldOf(AMOUNT).forGetter { it.progress.amount }
            ).apply(instance) { amount -> DamageTakenTransformationProgress().apply { updateProgress(Progress(amount)) } }
        }

        fun supports(pokemon: Pokemon): Boolean {
            return pokemon.form.evolutions.any { evolution ->
                evolution.requirements.any { requirement ->
                    requirement is DamageTakenRequirement
                }
            }
        }

    }

}