/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.fabric.mixin;

import com.cobblemon.mod.common.CobblemonNetwork;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.pokemon.update.evolution.ClientboundSeatAssignmentPacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(ServerEntity.class)
public class ServerEntityMixin {

    @Shadow
    @Final
    private Entity entity;

    @Inject(method = "sendPairingData", at = @At("TAIL"))
    private void cobblemon$sendPairingSeatAssignments(
            ServerPlayer player,
            Consumer<Packet<ClientGamePacketListener>> consumer,
            CallbackInfo ci
    ) {
        if (!(entity instanceof PokemonEntity pokemon)) return;
        if (pokemon.getOccupiedSeats().isEmpty()) return;

        pokemon.getOccupiedSeats().forEach((seat, passenger) -> {
            if (seat.getLocator() == null) return;
            CobblemonNetwork.INSTANCE.sendPacketToPlayer(
                    player,
                    new ClientboundSeatAssignmentPacket(passenger.getId(), pokemon.getId(), seat.getLocator())
            );
        });
    }
}
