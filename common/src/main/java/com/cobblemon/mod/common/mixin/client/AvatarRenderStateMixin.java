/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.duck.AvatarRenderStateDuck;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public class AvatarRenderStateMixin implements AvatarRenderStateDuck {
    @Unique
    private @Nullable AbstractClientPlayer cobblemon$mountedPlayer;

    @Unique
    private @Nullable PokemonEntity cobblemon$mountedPokemon;

    @Override
    public @Nullable AbstractClientPlayer cobblemon$getMountedPlayer() {
        return this.cobblemon$mountedPlayer;
    }

    @Override
    public void cobblemon$setMountedPlayer(@Nullable AbstractClientPlayer player) {
        this.cobblemon$mountedPlayer = player;
    }

    @Override
    public @Nullable PokemonEntity cobblemon$getMountedPokemon() {
        return this.cobblemon$mountedPokemon;
    }

    @Override
    public void cobblemon$setMountedPokemon(@Nullable PokemonEntity pokemonEntity) {
        this.cobblemon$mountedPokemon = pokemonEntity;
    }
}
