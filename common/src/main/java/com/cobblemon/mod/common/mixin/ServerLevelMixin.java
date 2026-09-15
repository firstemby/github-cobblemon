/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.duck.MinecraftServerDuck;
import com.cobblemon.mod.common.duck.ServerLevelDuck;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;


@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin extends Level implements ServerLevelDuck {

    // Ignored by Mixin, fulfils Java contract
    protected ServerLevelMixin(
            WritableLevelData levelData,
            ResourceKey<Level> dimension,
            RegistryAccess registryAccess,
            Holder<DimensionType> dimensionTypeRegistration,
            Supplier<ProfilerFiller> profiler,
            boolean isClientSide,
            boolean isDebug,
            long biomeZoomSeed,
            int maxChainedNeighborUpdates
    ) {
        super(levelData, dimension, registryAccess, dimensionTypeRegistration, profiler, isClientSide, isDebug, biomeZoomSeed, maxChainedNeighborUpdates);
    }

    @Shadow
    public abstract MinecraftServer shadow$getServer();

    /**
     * Cached to speed up level world key checks every tick.
     */
    private ResourceLocation cobblemon$levelResourceLocation;

    @Override
    public ResourceLocation cobblemon$getLevelResourceLocation() {
        if (this.cobblemon$levelResourceLocation == null) {
            this.cobblemon$levelResourceLocation = ((MinecraftServerDuck) this.shadow$getServer())
                    .cobblemon$getLevelKey(this)
                    .map(ResourceKey::location)
                    .orElse(null);
        }
        return this.cobblemon$levelResourceLocation;
    }

    @Inject(method = "findLightningRod", at = @At(value = "RETURN"), cancellable = true)
    private void cobblemon$findLightningRod(BlockPos blockPos, CallbackInfoReturnable<Optional<BlockPos>> ci) {
        final ServerLevel serverLevel = (ServerLevel) (Object) this;
        if (ci.getReturnValue().isEmpty()) {
            // Search for Pokemon with the ability Lightning Rod and redirect to them
            AABB aABB = AABB.encapsulatingFullBlocks(blockPos, new BlockPos(blockPos.atY(serverLevel.getMaxBuildHeight()))).inflate(64.0);
            List<LivingEntity> list = serverLevel.getEntitiesOfClass(LivingEntity.class, aABB, (livingEntity) -> livingEntity != null
                    && livingEntity.isAlive()
                    && livingEntity instanceof PokemonEntity
                    && ((PokemonEntity) livingEntity).getBeamMode() == 0
                    && ((PokemonEntity) livingEntity).getPokemon().getAbility().getName().equals("lightningrod"));
            if (!list.isEmpty()) {
                ci.setReturnValue(Optional.of((list.get(serverLevel.random.nextInt(list.size()))).blockPosition()));
            }
        }
    }
}
