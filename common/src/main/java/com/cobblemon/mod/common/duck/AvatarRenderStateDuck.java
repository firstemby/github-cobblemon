/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.duck;

import net.minecraft.client.player.AbstractClientPlayer;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import org.jetbrains.annotations.Nullable;

public interface AvatarRenderStateDuck {
    @Nullable AbstractClientPlayer cobblemon$getMountedPlayer();
    void cobblemon$setMountedPlayer(@Nullable AbstractClientPlayer player);

    @Nullable PokemonEntity cobblemon$getMountedPokemon();
    void cobblemon$setMountedPokemon(@Nullable PokemonEntity pokemonEntity);
}
