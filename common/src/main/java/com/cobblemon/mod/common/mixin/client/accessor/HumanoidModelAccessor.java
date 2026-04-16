/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin.client.accessor;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(HumanoidModel.class)
public interface HumanoidModelAccessor {
    @Invoker("poseRightArm")
    void cobblemon$invokePoseRightArm(HumanoidRenderState renderState);

    @Invoker("poseLeftArm")
    void cobblemon$invokePoseLeftArm(HumanoidRenderState renderState);

    @Invoker("setupAttackAnimation")
    void cobblemon$invokeSetupAttackAnimation(HumanoidRenderState renderState);
}
