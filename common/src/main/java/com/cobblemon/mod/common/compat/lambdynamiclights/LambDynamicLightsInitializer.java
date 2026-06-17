/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.compat.lambdynamiclights;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.client.particle.MoveDynamicLights;
import dev.lambdaurora.lambdynlights.api.DynamicLightsContext;
import dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer;
import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehaviorManager;
import dev.lambdaurora.lambdynlights.api.entity.luminance.EntityLuminance;
import dev.lambdaurora.lambdynlights.api.item.ItemLightSourceManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

public class LambDynamicLightsInitializer implements DynamicLightsInitializer {
    public static final EntityLuminance.Type POKEMON_LUMINANCE = EntityLuminance.Type.registerSimple(
            ResourceLocation.fromNamespaceAndPath(Cobblemon.MODID, "pokemon"),
            PokemonLuminance.INSTANCE
    );
    public static final EntityLuminance.Type PLAYER_LUMINANCE = EntityLuminance.Type.registerSimple(
            ResourceLocation.fromNamespaceAndPath(Cobblemon.MODID, "shoulder"),
            PlayerLuminance.INSTANCE
    );

    @Override
    public void onInitializeDynamicLights(DynamicLightsContext context) {
        Cobblemon.LOGGER.info("Lamb Dynamic Lights compatibility enabled");

        context.entityLightSourceManager().onRegisterEvent().register(ctx -> {
            ctx.register(EntityType.PLAYER, PlayerLuminance.INSTANCE);
        });

        // Attach a monochrome dynamic light to glowing particle effects (moves). Beam-like (targeted)
        // effects get a segment light covering the whole path; everything else a point light at the
        // emitter. Each behavior reports isRemoved() when its effect despawns, so the manager cleans it
        // up automatically. Only effects whose author set lightEmission > 0 are registered (see
        // ParticleStorm.emitsLight).
        final DynamicLightBehaviorManager behaviorManager = context.dynamicLightBehaviorManager();
        MoveDynamicLights.setSink(storm -> behaviorManager.add(
                storm.isBeam() ? new SnowstormBeamLight(storm) : new SnowstormDynamicLight(storm)
        ));
    }

    @SuppressWarnings({"removal", "UnstableApiUsage"})
    @Override
    public void onInitializeDynamicLights(ItemLightSourceManager itemLightSourceManager) {}
}

