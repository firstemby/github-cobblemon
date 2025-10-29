/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.component.FireworkExplosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

@Mixin(FireworkExplosion.Shape.class)
public abstract class FireworkExplosionShapeMixin {

    @Shadow
    @Final
    @Mutable
    private static FireworkExplosion.Shape[] $VALUES;


    @Shadow
    @Final
    @Mutable
    private static IntFunction<FireworkExplosion.Shape> BY_ID;

    @Shadow
    @Final
    @Mutable
    public static StreamCodec<ByteBuf, FireworkExplosion.Shape> STREAM_CODEC;

    @Shadow
    @Final
    @Mutable
    public static Codec<FireworkExplosion.Shape> CODEC;

    @Invoker("<init>")
    private static FireworkExplosion.Shape cobblemon$createFireworkExplosionShape(String enumName, int internalId, int id, String name) {
        throw new AssertionError();
    }

    @Inject(
        method = "<clinit>",
        at = @At("TAIL")
    )
    private static void cobblemon$addFireworkExplosionShape(CallbackInfo ci) {
        ArrayList<FireworkExplosion.Shape> types = new ArrayList<>(List.of($VALUES));

        types.add(cobblemon$createFireworkExplosionShape("POKEBALL", $VALUES.length, 5, "pokeball"));

        $VALUES = types.toArray(FireworkExplosion.Shape[]::new);

        BY_ID = ByIdMap.continuous(FireworkExplosion.Shape::getId, FireworkExplosion.Shape.values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, FireworkExplosion.Shape::getId);
        CODEC = StringRepresentable.fromValues(FireworkExplosion.Shape::values);
    }
}
