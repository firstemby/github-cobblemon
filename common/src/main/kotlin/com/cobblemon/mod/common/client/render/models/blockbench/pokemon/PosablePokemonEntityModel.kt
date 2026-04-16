/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.models.blockbench.pokemon

import com.cobblemon.mod.common.client.MountedPokemonAnimationRenderController
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.client.render.models.blockbench.PosableEntityModel
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

class PosablePokemonEntityModel : PosableEntityModel<PokemonEntity>() {
    override fun setupEntityTypeContext(entity: Entity?) {
        super.setupEntityTypeContext(entity)
        (entity as? PokemonEntity)?.let {
            context.put(RenderContext.SCALE, it.pokemon.form.baseScale)
            context.put(RenderContext.SPECIES, it.pokemon.species.resourceIdentifier)
            context.put(RenderContext.ASPECTS, it.pokemon.aspects)
            VaryingModelRepository.getTexture(it.pokemon.species.resourceIdentifier, it.delegate as PokemonClientDelegate)
                .let { texture -> context.put(RenderContext.TEXTURE, texture) }
        }
    }

    override fun getTicksForAnimation(
        entity: Entity?,
        limbSwing: Float,
        limbSwingAmount: Float,
        ageInTicks: Float,
        headYaw: Float,
        headPitch: Float
    ): Float {
        val pokemonEntity = entity as? PokemonEntity ?: return ageInTicks
        val delegate = pokemonEntity.delegate as? PosableState
        val forcedTick = MountedPokemonAnimationRenderController.getPartialTick(pokemonEntity)
        if (pokemonEntity.passengers.isEmpty() || delegate == null || forcedTick == null) {
            return ageInTicks
        }
        delegate.updatePartialTicks(forcedTick)
        val entityPos = Vec3(
            Mth.lerp(forcedTick.toDouble(), pokemonEntity.xOld, pokemonEntity.x),
            Mth.lerp(forcedTick.toDouble(), pokemonEntity.yOld, pokemonEntity.y),
            Mth.lerp(forcedTick.toDouble(), pokemonEntity.zOld, pokemonEntity.z)
        )
        delegate.updateLocatorPosition(entityPos)
        return forcedTick
    }
}
