/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.sensing.PiglinSpecificSensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(PiglinSpecificSensor.class)
public abstract class PiglinSpecificSensorMixin {

    @Inject(method = "doTick", at = @At("TAIL"))
    protected void cobblemon$doTick(ServerLevel level, LivingEntity entity, CallbackInfo ci) {
        Brain<?> brain = entity.getBrain();
        if (!brain.hasMemoryValue(MemoryModuleType.NEAREST_VISIBLE_ZOMBIFIED)) {
            NearestVisibleLivingEntities nearestVisibleLivingEntities = brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).orElse(NearestVisibleLivingEntities.empty());
            Optional<LivingEntity> fearedEntity = Optional.empty();
            for (LivingEntity livingEntity : nearestVisibleLivingEntities.findAll((x) -> true)) {
                if (fearedEntity.isEmpty() && livingEntity instanceof PokemonEntity && ((PokemonEntity) livingEntity).getPokemon().getSpecies().getBehaviour().getEntityInteract().getAvoidedByPiglin()) {
                    fearedEntity = Optional.of(livingEntity);
                    brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_ZOMBIFIED, livingEntity);
                }
            }
        }
    }
}
