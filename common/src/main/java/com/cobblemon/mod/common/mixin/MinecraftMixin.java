package com.cobblemon.mod.common.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.Music;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    /** Used by MusicManager to dispatch the music to play. */
    @WrapOperation(
        method = "getSituationalMusic",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;getBackgroundMusic()Ljava/util/Optional;")
    )
    public Optional<Music> getBackgroundMusic(Biome instance, Operation<Optional<Music>> original) {
        return original.call(instance);
    }
}
