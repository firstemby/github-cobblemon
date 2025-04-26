/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonServerDelegate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Inject(method = "shouldDiscardEntity", at = @At(value = "HEAD"), cancellable = true)
    public void cobblemon$allowPokemonSpawns(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (entity instanceof PokemonEntity) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "levelEvent(Lnet/minecraft/world/entity/player/Player;ILnet/minecraft/core/BlockPos;I)V", at = @At(value = "HEAD"))
    public void cobblemon$setPokemonDancing(Player player, int type, BlockPos pos, int data, CallbackInfo ci) {
        if (type == LevelEvent.SOUND_PLAY_JUKEBOX_SONG || type == LevelEvent.SOUND_STOP_JUKEBOX_SONG) {
            cobblemon$notifyNearbyEntities((ServerLevel) ((Object) this), pos, type == LevelEvent.SOUND_PLAY_JUKEBOX_SONG);
        }
    }

    @Unique
    private void cobblemon$notifyNearbyEntities(ServerLevel level, BlockPos pos, boolean playing) {
        for (PokemonEntity pokemonEntity : level.getEntitiesOfClass(PokemonEntity.class, new AABB(pos).inflate(Cobblemon.config.getPokemonDanceDistance()))) {
            PokemonServerDelegate delegate = (PokemonServerDelegate) pokemonEntity.getDelegate();
            delegate.setDancing(playing);
            delegate.setJukeboxPos(pos);
            pokemonEntity.setRecordPlayingNearby(pos, playing);
        }
    }
}
