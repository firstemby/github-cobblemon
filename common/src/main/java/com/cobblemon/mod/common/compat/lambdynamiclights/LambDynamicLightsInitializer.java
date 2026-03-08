/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.compat.lambdynamiclights;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonEntities;
import dev.lambdaurora.lambdynlights.api.DynamicLightsContext;
import dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer;
import dev.lambdaurora.lambdynlights.api.entity.luminance.EntityLuminance;
import dev.lambdaurora.lambdynlights.api.item.ItemLightSourceManager;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

public class LambDynamicLightsInitializer implements DynamicLightsInitializer {
    public static final EntityLuminance.Type POKEMON_LUMINANCE = EntityLuminance.Type.registerSimple(
            Identifier.fromNamespaceAndPath(Cobblemon.MODID, "pokemon"),
            PokemonLuminance.INSTANCE
    );
    public static final EntityLuminance.Type PLAYER_LUMINANCE = EntityLuminance.Type.registerSimple(
            Identifier.fromNamespaceAndPath(Cobblemon.MODID, "shoulder"),
            PlayerLuminance.INSTANCE
    );

    @Override
    public void onInitializeDynamicLights(DynamicLightsContext context) {
        Cobblemon.LOGGER.info("Lamb Dynamic Lights compatibility enabled");

        context.entityLightSourceManager().onRegisterEvent().register(ctx -> {
            ctx.register(EntityType.PLAYER, PlayerLuminance.INSTANCE);
            ctx.register(CobblemonEntities.POKEMON, PokemonLuminance.INSTANCE);
        });
    }
}

