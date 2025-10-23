package com.cobblemon.mod.common.duck;

import net.minecraft.client.sounds.AudioStream;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.CompletableFuture;

public interface SoundBufferLibraryDuck {

    CompletableFuture<AudioStream> getStreamWithLoopPoints(ResourceLocation audioLocation, int loopStart, int loopEnd);

    CompletableFuture<AudioStream> getStreamWithIntro(ResourceLocation introLocation, ResourceLocation endLocation, boolean isLoop);

    CompletableFuture<AudioStream> getStreamWithIntro(ResourceLocation introLocation, ResourceLocation endLocation, int loopStart, int loopEnd);
}
