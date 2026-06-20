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

        // Attach a monochrome dynamic light to each glowing particle. The light follows its particle and
        // reports isRemoved() when the particle dies, so the manager cleans it up. The cap on how many
        // particles per emitter may be lit at once is enforced at particle spawn, not here.
        final DynamicLightBehaviorManager behaviorManager = context.dynamicLightBehaviorManager();
        MoveDynamicLights.setSink(particle -> behaviorManager.add(new SnowstormParticleLight(particle)));
    }

    @SuppressWarnings({"removal", "UnstableApiUsage"})
    @Override
    public void onInitializeDynamicLights(ItemLightSourceManager itemLightSourceManager) {}
}

