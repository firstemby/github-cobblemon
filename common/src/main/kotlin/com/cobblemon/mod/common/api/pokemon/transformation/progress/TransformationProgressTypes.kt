/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokemon.transformation.progress

import com.cobblemon.mod.common.pokemon.transformation.controller.ServerEvolutionController
import com.cobblemon.mod.common.pokemon.transformation.progress.*
import com.cobblemon.mod.common.util.cobblemonResource
import com.mojang.serialization.Codec
import com.mojang.serialization.Lifecycle
import net.minecraft.core.MappedRegistry
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import kotlin.jvm.Throws

object TransformationProgressTypes {

    private val REGISTRY: Registry<TransformationProgressType<*>> = MappedRegistry(
        ResourceKey.createRegistryKey(cobblemonResource("transformation_progress")), Lifecycle.stable()
    )

    @JvmStatic
    val DAMAGE_TAKEN = this.registerType(DamageTakenTransformationProgress.ID, TransformationProgressType(DamageTakenTransformationProgress.CODEC))
    @JvmStatic
    val DEFEAT = this.registerType(DefeatTransformationProgress.ID, TransformationProgressType(DefeatTransformationProgress.CODEC))
    @JvmStatic
    val LAST_BATTLE_CRITICAL_HITS = this.registerType(LastBattleCriticalHitsTransformationProgress.ID, TransformationProgressType(LastBattleCriticalHitsTransformationProgress.CODEC))
    @JvmStatic
    val RECOIL = this.registerType(RecoilTransformationProgress.ID, TransformationProgressType(RecoilTransformationProgress.CODEC))
    @JvmStatic
    val USE_MOVE = this.registerType(UseMoveTransformationProgress.ID, TransformationProgressType(UseMoveTransformationProgress.CODEC))

    /**
     * Registers an evolution progress.
     *
     * @param T The type of the [TransformationProgress].
     * @param id The [ResourceLocation].
     * @param type The [TransformationProgressType] record.
     * @return The registered [TransformationProgressType] of type [T].
     *
     * @throws [IllegalStateException] if [Registry.register] fails.
     */
    @Throws(IllegalStateException::class)
    @JvmStatic
    fun <T : TransformationProgress<*>> registerType(id: ResourceLocation, type: TransformationProgressType<T>): TransformationProgressType<T> {
        return Registry.register(REGISTRY, id, type)
    }

    /**
     * Creates a codec instance of this registry.
     *
     * @return The generated [Codec] of [TransformationProgress].
     */
    fun codec(): Codec<TransformationProgress<*>> = REGISTRY.byNameCodec().dispatch(
        ServerEvolutionController.ID_KEY,
        { it.type() },
        { it.codec }
    )

}