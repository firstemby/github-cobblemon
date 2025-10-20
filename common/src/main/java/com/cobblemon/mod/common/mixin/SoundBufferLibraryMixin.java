package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.client.sound.streams.CompoundAudioStream;
import com.cobblemon.mod.common.client.sound.streams.LoopPointAudioStream;
import com.cobblemon.mod.common.duck.SoundBufferLibraryDuck;
import net.minecraft.Util;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.LoopingAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Mixin(SoundBufferLibrary.class)
public class SoundBufferLibraryMixin implements SoundBufferLibraryDuck {

    @Shadow
    private ResourceProvider resourceManager;

    /** */
    @Override
    public CompletableFuture<AudioStream> getStreamWithLoopPoints(ResourceLocation audioLocation, int loopStart, int loopEnd) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                InputStream introStream = this.resourceManager.open(audioLocation);
                InputStream secondaryStream = this.resourceManager.open(audioLocation);
                return new LoopPointAudioStream(JOrbisAudioStream::new, introStream, loopStart, loopEnd);
            } catch (IOException var4) {
                throw new CompletionException(var4);
            }
        }, Util.nonCriticalIoPool());
    }

    /** */
    @Override
    public CompletableFuture<AudioStream> getStreamWithIntro(ResourceLocation introLocation, ResourceLocation endLocation, boolean isLoop) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ResourceLocation test = ResourceLocation.fromNamespaceAndPath(introLocation.getNamespace(), "sounds/" + introLocation.getPath() + ".ogg");

                InputStream introInputStream = this.resourceManager.open(test);
                InputStream endInputStream = this.resourceManager.open(endLocation);

                AudioStream introStream = new JOrbisAudioStream(introInputStream);
                AudioStream endStream = isLoop ? new LoopingAudioStream(JOrbisAudioStream::new, endInputStream) : new JOrbisAudioStream(endInputStream);
                return new CompoundAudioStream(introStream, endStream);
            } catch (IOException var4) {
                throw new CompletionException(var4);
            }
        }, Util.nonCriticalIoPool());
    }
}
