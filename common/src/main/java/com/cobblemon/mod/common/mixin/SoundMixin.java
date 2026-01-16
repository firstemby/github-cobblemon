package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.duck.SoundDuck;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;

import static net.minecraft.client.resources.sounds.Sound.SOUND_LISTER;

@Mixin(Sound.class)
public abstract class SoundMixin implements SoundDuck {

    int loopStart = 0;

    int loopEnd = 0;

    ResourceLocation intro = null;

    @Override
    public ResourceLocation getIntro() {
        return SOUND_LISTER.idToFile(this.intro);
    }

    @Override
    public int getLoopStart() {
        return loopStart;
    }

    @Override
    public int getLoopEnd() {
        return loopEnd;
    }

    @Override
    public void setLoopStart(int startInSamples) {
        this.loopStart = startInSamples;
    }

    @Override
    public void setLoopEnd(int endInSamples) {
        this.loopEnd = endInSamples;
    }

    @Override
    public void setIntro(ResourceLocation intro) {
        this.intro = intro;
    }

    @Override
    public boolean hasLoopPoints() {
        return (this.loopEnd >= 0 && this.loopStart >= 0) && (this.loopEnd + this.loopStart != 0) && (loopStart < loopEnd || loopStart == 0 || loopEnd == 0);
    }
    // this is used to determine if the LoopPointAudioStream is necessary or if the legacy LoopingAudioStream can be used

    @Override
    public boolean hasIntro() {
        return this.intro != null && !this.intro.getPath().isBlank();
    }
}
