package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.duck.SoundDuck;
import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundEventRegistrationSerializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SoundEventRegistrationSerializer.class)
public class SoundEventRegistrationSerializerMixin {

    /** Injects custom fields into Sound resource for loop behavior. */
    @ModifyReturnValue(method = "getSound", at = @At("RETURN"))
    private Sound getSound(Sound original, JsonObject json) {
        int loopStart = GsonHelper.getAsInt(json, "loopStart", 0);
        int loopEnd = GsonHelper.getAsInt(json, "loopEnd", 0);
        ResourceLocation intro = ResourceLocation.parse(GsonHelper.getAsString(json, "intro", ""));
        ((SoundDuck)original).setLoopStart(loopStart);
        ((SoundDuck)original).setLoopEnd(loopEnd);
        ((SoundDuck)original).setIntro(intro);
        return original;
    }
}
