/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.neoforge.mixin;

import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public class CobblemonNeoForgeMixinConfigPlugin implements IMixinConfigPlugin {
    private static final String ADORN_REGISTER_INVOKER = "com.cobblemon.mod.common.mixin.invoker.AdornRegisterInvoker";
    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.equalsIgnoreCase("com.cobblemon.mod.common.mixin.OptionalEntityMixin")) {
            return FMLLoader.getCurrent().getLoadingModList().getMods().stream().noneMatch(mod -> Objects.equals(mod.getModId(), "moonrise"));
        }
        else if (ADORN_REGISTER_INVOKER.equals(mixinClassName)) {
            return FMLLoader.getCurrent().getLoadingModList().getMods().stream().anyMatch(mod -> Objects.equals(mod.getModId(), "adorn"));
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return List.of();
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
