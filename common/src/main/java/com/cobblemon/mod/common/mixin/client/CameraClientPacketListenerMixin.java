/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client;

import com.cobblemon.mod.common.client.camera.CameraClientChunkCacheExtension;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class CameraClientPacketListenerMixin {
    @Shadow
    private ClientLevel level;

    @Inject(method = "queueLightRemoval", at = @At("HEAD"), cancellable = true)
    private void cobblemon$keepCameraLight(ClientboundForgetLevelChunkPacket packet, CallbackInfo ci) {
        ChunkPos position = packet.pos();
        LevelChunk retained = CameraClientChunkCacheExtension.getChunk(position.x, position.z);
        if (retained != null && retained.getLevel() == level) {
            ci.cancel();
        }
    }
}
