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
import com.cobblemon.mod.common.pokemon.transformation.form.PermanentForm
import com.cobblemon.mod.common.pokemon.transformation.requirements.BattleCriticalHitsRequirement
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.resources.ResourceLocation
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder

class LastBattleCriticalHitsTransformationProgress : TransformationProgress<LastBattleCriticalHitsTransformationProgress.Progress> {

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

    override fun type(): TransformationProgressType<*> = TransformationProgressTypes.LAST_BATTLE_CRITICAL_HITS

    data class Progress(val amount: Int)

    companion object {

        val ID = cobblemonResource(BattleCriticalHitsRequirement.ADAPTER_VARIANT)
        private const val AMOUNT = "amount"

        @JvmStatic
        val CODEC: MapCodec<LastBattleCriticalHitsTransformationProgress> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                Codec.intRange(0, Int.MAX_VALUE).fieldOf(AMOUNT).forGetter { it.progress.amount }
            ).apply(instance) { amount -> LastBattleCriticalHitsTransformationProgress().apply { updateProgress(Progress(amount)) } }
        }

        fun supports(pokemon: Pokemon): Boolean {
            val form = pokemon.form
            if (form !is PermanentForm) return false
            return form.transformations.any { transformation ->
                transformation.requirements.any { requirement ->
                    requirement is BattleCriticalHitsRequirement
                }
            }
        }

    }

}