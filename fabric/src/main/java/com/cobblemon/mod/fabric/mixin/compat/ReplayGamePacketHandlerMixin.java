/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.fabric.mixin.compat;

import com.cobblemon.mod.common.api.mark.Mark;
import com.cobblemon.mod.common.api.mark.Marks;
import com.cobblemon.mod.common.api.pokeball.PokeBalls;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.spawn.SpawnExtraDataEntityPacket;
import com.cobblemon.mod.common.net.messages.client.spawn.SpawnPokemonPacket;
import com.cobblemon.mod.common.pokeball.PokeBall;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.moulberry.flashback.playback.ReplayGamePacketHandler;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(ReplayGamePacketHandler.class)
public abstract class ReplayGamePacketHandlerMixin {
    @Shadow
    public abstract void handleAddEntity(ClientboundAddEntityPacket clientboundAddEntityPacket);

    @Shadow(remap = false)
    private Int2ObjectMap<Entity> pendingEntities;

    @Inject(method = "handleCustomPayload", at = @At("HEAD"))
    public void cobblemon$handleSpawnEntityWithExtraData(ClientboundCustomPayloadPacket packet, CallbackInfo ci) {
        if (packet.payload() instanceof SpawnExtraDataEntityPacket<?, ?> cobblemonPacket) {
            ClientboundAddEntityPacket addEntityPacket = cobblemonPacket.getVanillaSpawnPacket();
            this.handleAddEntity(addEntityPacket);

            Entity entity = pendingEntities.get(addEntityPacket.getId());
            if (entity instanceof PokemonEntity pokemonEntity && cobblemonPacket instanceof SpawnPokemonPacket spawnPokemonPacket) {
                Pokemon pokemon = pokemonEntity.getPokemon();
                pokemon.setUuid(spawnPokemonPacket.getPokemonUUID());
                Species species = Optional.ofNullable(PokemonSpecies.INSTANCE.getByIdentifier(spawnPokemonPacket.getSpeciesId()))
                                    .orElse(PokemonSpecies.INSTANCE.random());
                pokemon.setSpecies(species);
                pokemon.setScaleModifier(spawnPokemonPacket.getScaleModifier());
                pokemon.setGender(spawnPokemonPacket.getGender());
                pokemon.setShiny(spawnPokemonPacket.getShiny());
                pokemon.setForm(pokemon.getSpecies().getFormByShowdownId(spawnPokemonPacket.getFormName()));
                pokemon.setForcedAspects(spawnPokemonPacket.getAspects());
                pokemon.setNickname(spawnPokemonPacket.getNickname());
                ResourceLocation markId = spawnPokemonPacket.getMark();
                if (markId != null) {
                    pokemon.setActiveMark(Marks.INSTANCE.getByIdentifier(spawnPokemonPacket.getMark()));
                }
                PokeBall caughtBall = PokeBalls.INSTANCE.getPokeBall(spawnPokemonPacket.getCaughtBall());
                if (caughtBall != null) {
                    pokemon.setCaughtBall(caughtBall);
                }
            }
        }
    }
}
