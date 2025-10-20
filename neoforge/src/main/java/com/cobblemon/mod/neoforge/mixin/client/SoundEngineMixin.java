package com.cobblemon.mod.neoforge.mixin.client;

import com.cobblemon.mod.common.duck.SoundBufferLibraryDuck;
import com.cobblemon.mod.common.duck.SoundDuck;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CompletableFuture;

@Mixin(SoundEngine.class)
public class SoundEngineMixin {

    /**
     * Dispatch custom stream for sounds that have an intro. This is needed because NeoForge overwrites the SoundBufferLibrary$getStream invocation.
     * Because this is a WrapOperation it's compatible with NeoForge's PlayStreamingSourceEvent.
     */
    @WrapOperation(method = "play", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/sounds/SoundInstance;getStream(Lnet/minecraft/client/sounds/SoundBufferLibrary;Lnet/minecraft/client/resources/sounds/Sound;Z)Ljava/util/concurrent/CompletableFuture;"))
    public CompletableFuture<AudioStream> getStream(SoundInstance soundInstance, SoundBufferLibrary bufferLibrary, Sound sound, boolean looping, Operation<CompletableFuture<AudioStream>> original) {
        SoundDuck soundDuck = (SoundDuck)sound;
        SoundBufferLibraryDuck libraryDuck = (SoundBufferLibraryDuck)bufferLibrary;
        if (soundDuck.hasIntro() || (soundDuck.hasLoopPoints() && looping)) {
            return libraryDuck.getCompoundStream();
        }
        else {
            return original.call(soundInstance, bufferLibrary, sound, looping);
        }
    }
}
