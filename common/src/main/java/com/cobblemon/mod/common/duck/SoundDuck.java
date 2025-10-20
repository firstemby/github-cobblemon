package com.cobblemon.mod.common.duck;

import net.minecraft.resources.ResourceLocation;

public interface SoundDuck {

    int getLoopStart();

    int getLoopEnd();

    ResourceLocation getIntro();

    void setLoopStart(int startInSamples);

    void setLoopEnd(int endInSamples);

    void setIntro(ResourceLocation intro);

    boolean hasLoopPoints();

    boolean hasIntro();
}
