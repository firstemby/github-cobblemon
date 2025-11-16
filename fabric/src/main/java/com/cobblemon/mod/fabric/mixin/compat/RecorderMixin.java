/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.fabric.mixin.compat;

import com.cobblemon.mod.common.duck.PlayerDuck;
import com.cobblemon.mod.common.net.messages.client.orientation.ClientboundUpdateDriverInputPacket;
import com.llamalad7.mixinextras.sugar.Local;
import com.moulberry.flashback.record.Recorder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(Recorder.class)
public abstract class RecorderMixin {
    @Inject(method = "writeLocalData()V", at = @At(value = "INVOKE", target = "Lcom/moulberry/flashback/io/AsyncReplaySaver;writeGamePackets(Lnet/minecraft/network/codec/StreamCodec;Ljava/util/List;)V"))
    public void cobblemon$writeLocalData(
            CallbackInfo ci,
            @Local(name = "gamePackets") List<Packet<? super ClientGamePacketListener>> gamePackets
    ) {
        // The server will not send a ClientboundUpdateDriverInputPacket to the player that initially set the driver input
        // so Flashback needs to save that data itself. For stuff like players holding space on Pokemon mounts
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        // Gather driver Input
        float vertInput = player.jumping ? 1.0f : (player.isShiftKeyDown() ? -1.0f : 0.0f);
        Vector3f driverInput = new Vector3f(Math.signum(player.xxa), vertInput, Math.signum(player.zza));
        gamePackets.add(new ClientboundCustomPayloadPacket(new ClientboundUpdateDriverInputPacket(driverInput, player.getId())));
    }
}
