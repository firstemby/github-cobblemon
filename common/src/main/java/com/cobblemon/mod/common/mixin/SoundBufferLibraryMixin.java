package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.Cobblemon;
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

    /**
     * Creates a LoopPointAudioStream with defined loop points.
     *
     * @param audioLocation The audio file to open for stream.
     * @param loopStart The starting point of the stream's loop.
     * @param loopEnd The ending point of the stream's loop.
     */
    @Override
    public CompletableFuture<AudioStream> getStreamWithLoopPoints(ResourceLocation audioLocation, int loopStart, int loopEnd) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                InputStream introStream = this.resourceManager.open(audioLocation);
                return new LoopPointAudioStream(JOrbisAudioStream::new, introStream, loopStart, loopEnd);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, Util.nonCriticalIoPool());
    }

    /**
     * Creates a CompoundAudioStream from a JOrbisAudioStream intro and LoopPointAudioStream end.
     *
     * @param introLocation The audio file to open for the intro stream.
     * @param endLocation The audio file to open for the end stream.
     * @param loopStart The starting point of the ending LoopPointAudioStream's loop.
     * @param loopEnd The ending point of the ending LoopPointAudioStream's loop.
     */
    @Override
    public CompletableFuture<AudioStream> getStreamWithIntro(ResourceLocation introLocation, ResourceLocation endLocation, int loopStart, int loopEnd) {
        return createCompoundStream(
            introLocation,
            endLocation,
            (inputStream) -> new LoopPointAudioStream(JOrbisAudioStream::new, inputStream, loopStart, loopEnd)
        );
    }

    /**
     * Creates a CompoundAudioStream from a JOrbisAudioStream intro and a JOrbisAudioStream or LoopingAudioStream end.
     *
     * @param introLocation The audio file to open for the intro stream.
     * @param endLocation The audio file to open for the end stream.
     * @param isLoop Whether the end stream loops (LoopingAudioStream) or not (JOrbisAudioStream).
     */
    @Override
    public CompletableFuture<AudioStream> getStreamWithIntro(ResourceLocation introLocation, ResourceLocation endLocation, boolean isLoop) {
        return createCompoundStream(
            introLocation,
            endLocation,
            !isLoop ? JOrbisAudioStream::new : (inputStream) -> new LoopingAudioStream(JOrbisAudioStream::new, inputStream)
        );
    }

    private CompletableFuture<AudioStream> createCompoundStream(ResourceLocation introLocation, ResourceLocation endLocation, LoopingAudioStream.AudioStreamProvider endProvider) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ResourceLocation fixed = ResourceLocation.fromNamespaceAndPath(introLocation.getNamespace(), "sounds/" + introLocation.getPath() + ".ogg");  // TODO it just works
                InputStream introInputStream = this.resourceManager.open(fixed);
                InputStream endInputStream = this.resourceManager.open(endLocation);

                AudioStream introStream = new JOrbisAudioStream(introInputStream);
                AudioStream endStream = endProvider.create(endInputStream);
                return new CompoundAudioStream(introStream, endStream);
            } catch (Exception e) {
                Cobblemon.LOGGER.error(e.getMessage());
                throw new CompletionException(e);
            }
        }, Util.nonCriticalIoPool());
    }
}
