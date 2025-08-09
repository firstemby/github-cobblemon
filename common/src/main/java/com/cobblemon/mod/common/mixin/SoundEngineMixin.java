/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.api.riding.sound.RideAttenuationModel;
import com.cobblemon.mod.common.api.riding.sound.RideLoopSound;
import com.cobblemon.mod.common.client.sound.BattleMusicController;
import com.cobblemon.mod.common.duck.ChannelDuck;
import com.cobblemon.mod.common.duck.SoundEngineDuck;
import com.google.common.collect.Multimap;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Iterator;
import java.util.Map;

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin implements SoundEngineDuck {

    @Shadow
    private boolean loaded;

    @Shadow
    private Multimap<SoundSource, SoundInstance> instanceBySource;

    @Shadow @Final
    private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;

    @Shadow
    protected abstract void stop(SoundInstance sound);

    @Shadow
    public abstract boolean isActive(SoundInstance sound);

    @Inject(method = "play",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/sounds/ChannelAccess$ChannelHandle;execute(Ljava/util/function/Consumer;)V"
            ),
            cancellable = true
    )
    private void cobblemon$overrideChannelExecute(
            SoundInstance sound,
            CallbackInfo ci,
            @Local(name = "h") float h,
            @Local(name = "i") float i,
            @Local(name = "g") float g,
            @Local(name = "bl2") boolean bl2,
            @Local(name = "bl3") boolean bl3,
            @Local(name = "bl") boolean bl,
            @Local(name = "vec3") Vec3 vec3,
            @Local(name = "channelHandle") ChannelAccess.ChannelHandle handle
    ) {
        if (!(sound instanceof RideLoopSound rideSound)) return;

        if (handle == null) return;

        handle.execute((channel) -> {
            channel.setPitch(i);
            channel.setVolume(h);

            if (rideSound.getRideAttenuation() == RideAttenuationModel.LINEAR) {
                channel.linearAttenuation(g);
            } else if (rideSound.getRideAttenuation() == RideAttenuationModel.EXPONENTIAL){
                ((ChannelDuck) channel).cobblemon$inverseAttenuation(g);
            } else {
                channel.disableAttenuation();
            }

            // Apply low pass filter if the rideSound dictate that it should be muffled
            if (rideSound.getShouldMuffle()) {
                ((ChannelDuck) channel).cobblemon$applyLowPassFilter(1.0f, rideSound.getMuffleAmount());
            } else {
                ((ChannelDuck) channel).cobblemon$clearFilters();
            }

            channel.setLooping(bl2 && !bl3);
            channel.setSelfPosition(vec3);
            channel.setRelative(bl);
        });
    }

    @Inject(
            method = "tickNonPaused",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/sounds/ChannelAccess$ChannelHandle;execute(Ljava/util/function/Consumer;)V",
                    ordinal = 0
            )
    )
    private void cobblemon$tickableSoundTick(
            CallbackInfo ci,
            @Local(name = "tickableSoundInstance") TickableSoundInstance instance,
            @Local(name = "channelHandle", ordinal = 0) ChannelAccess.ChannelHandle handle,
            @Local(name = "f", ordinal = 0) float volume,
            @Local(name = "g", ordinal = 0) float pitch,
            @Local(name = "vec3", ordinal = 0) Vec3 vec3
    ) {
        if (!(instance instanceof RideLoopSound rideSound)) return;

        handle.execute(channel -> {
            channel.setVolume(volume);
            channel.setPitch(pitch);
            channel.setSelfPosition(vec3);

            // Apply low pass filter if the rideSound dictate that it should be muffled
            if (rideSound.getShouldMuffle()) {
                ((ChannelDuck) channel).cobblemon$applyLowPassFilter(1.0f, rideSound.getMuffleAmount());
            } else {
                ((ChannelDuck) channel).cobblemon$clearFilters();
            }
        });
    }

    /** Resumes the Source belonging to the queried SoundInstance. */
    private void resume(SoundInstance sound) {
        ChannelAccess.ChannelHandle sourceManager = this.instanceToChannel.get(sound);
        if (this.loaded && sourceManager != null) sourceManager.execute(Channel::unpause);
    }

    /** Pauses the Source belonging to the queried SoundInstance. */
    private void pause(SoundInstance sound) {
        ChannelAccess.ChannelHandle sourceManager = this.instanceToChannel.get(sound);
        if (this.loaded && sourceManager != null) sourceManager.execute(Channel::pause);
    }

    /** Resumes the SoundInstance(s) queried by id and/or category. */
    @Override
    public void resumeSounds(@Nullable ResourceLocation id, @Nullable SoundSource category) {
        if (category != null) {
            this.instanceBySource.get(category).forEach((sound) -> {
                if (id == null || sound.getLocation().equals(id)) resume(sound);
            });
        } else if (id == null) {
            this.instanceToChannel.keySet().forEach(this::resume);
        } else {
            this.instanceToChannel.keySet().forEach(sound -> {
                if (sound.getLocation().equals(id)) resume(sound);
            });
        }
    }

    /** Pauses the SoundInstances queried by id and/or category. */
    @Override
    public void pauseSounds(@Nullable ResourceLocation id, @Nullable SoundSource category) {
        if (category != null) {
            this.instanceBySource.get(category).forEach((sound) -> {
                if (id == null || sound.getLocation().equals(id)) pause(sound);
            });
        } else if (id == null) {
            this.instanceToChannel.keySet().forEach(this::pause);
        } else {
            this.instanceToChannel.keySet().forEach(sound -> {
                if (sound.getLocation().equals(id)) pause(sound);
            });
        }
    }

    /** Allows stopping all SoundInstances that belong to a queried category. */
    @Inject(method = "stop(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/sounds/SoundSource;)V", at = @At("HEAD"), cancellable = true)
    public void stopSounds(@Nullable ResourceLocation id, @Nullable SoundSource category, CallbackInfo cb) {
        if (id == null && category != null) {
            this.instanceBySource.get(category).forEach(this::stop);
            cb.cancel();
        }
    }

    /** Different behavior for resuming SoundInstances while a BattleMusicInstance is being played. We do not want to resume filtered sounds. */
    @Inject(method = "resume()V", at = @At("HEAD"), cancellable = true)
    public void resume(CallbackInfo cb) {
        if (this.isActive(BattleMusicController.INSTANCE.getMusic())) {
            this.instanceBySource.values().forEach(sound -> {
               if (sound == BattleMusicController.INSTANCE.getMusic() || !BattleMusicController.INSTANCE.getFilteredCategories().contains(sound.getSource())) {
                   resume(sound);
               }
            });
            cb.cancel();
        }
    }
}
