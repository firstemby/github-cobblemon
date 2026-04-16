/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.neoforge.mixin;

import com.cobblemon.mod.common.api.riding.sound.RideAttenuationModel;
import com.cobblemon.mod.common.api.riding.sound.RideLoopSound;
import com.cobblemon.mod.common.duck.ChannelDuck;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public class NeoforgeSoundEngineMixin {
    @Inject(method = "play",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/sounds/ChannelAccess$ChannelHandle;execute(Ljava/util/function/Consumer;)V"
            ),
            cancellable = true
    )
    private void cobblemon$overrideChannelExecute(
            SoundInstance sound,
            CallbackInfoReturnable<SoundEngine.PlayResult> cir,
            @Local(name = "volume") float volume,
            @Local(name = "pitch") float pitch,
            @Local(name = "attenuationDistance") float attenuationDistance,
            @Local(name = "isLooping") boolean isLooping,
            @Local(name = "isStreaming") boolean isStreaming,
            @Local(name = "isRelative") boolean isRelative,
            @Local(name = "position") Vec3 position,
            @Local(name = "handle") ChannelAccess.ChannelHandle handle
    ) {
        if (!(sound instanceof RideLoopSound rideSound)) return;

        if (handle == null) return;

        handle.execute((channel) -> {
            channel.setPitch(pitch);
            channel.setVolume(volume);

            if (rideSound.getRideAttenuation() == RideAttenuationModel.LINEAR) {
                channel.linearAttenuation(attenuationDistance);
            } else if (rideSound.getRideAttenuation() == RideAttenuationModel.EXPONENTIAL){
                ((ChannelDuck) channel).cobblemon$inverseAttenuation(attenuationDistance);
            } else {
                channel.disableAttenuation();
            }

            // Apply low pass filter if the rideSound dictate that it should be muffled
            if (rideSound.getShouldMuffle()) {
                ((ChannelDuck) channel).cobblemon$applyLowPassFilter(1.0f, rideSound.getMuffleAmount());
            } else {
                ((ChannelDuck) channel).cobblemon$clearFilters();
            }

            channel.setLooping(isLooping && !isStreaming);
            channel.setSelfPosition(position);
            channel.setRelative(isRelative);
        });
    }

    @Inject(
            method = "tickInGameSound",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/sounds/ChannelAccess$ChannelHandle;execute(Ljava/util/function/Consumer;)V",
                    ordinal = 0
            )
    )
    private void cobblemon$tickableSoundTick(
            CallbackInfo ci,
            @Local(name = "instance") TickableSoundInstance instance,
            @Local(name = "handle") ChannelAccess.ChannelHandle handle,
            @Local(name = "volume") float volume,
            @Local(name = "pitch") float pitch,
            @Local(name = "position") Vec3 position
    ) {
        if (!(instance instanceof RideLoopSound rideSound)) return;

        handle.execute(channel -> {
            channel.setVolume(volume);
            channel.setPitch(pitch);
            channel.setSelfPosition(position);

            // Apply low pass filter if the rideSound dictate that it should be muffled
            if (rideSound.getShouldMuffle()) {
                ((ChannelDuck) channel).cobblemon$applyLowPassFilter(1.0f, rideSound.getMuffleAmount());
            } else {
                ((ChannelDuck) channel).cobblemon$clearFilters();
            }
        });
    }
}
