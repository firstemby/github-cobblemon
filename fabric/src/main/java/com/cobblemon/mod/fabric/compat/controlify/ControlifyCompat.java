/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.fabric.compat.controlify;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.util.MiscUtilsKt;
import dev.isxander.controlify.api.ControlifyApi;
import dev.isxander.controlify.api.bind.ControlifyBindApi;
import dev.isxander.controlify.api.bind.InputBindingSupplier;
import dev.isxander.controlify.api.entrypoint.ControlifyEntrypoint;
import dev.isxander.controlify.api.event.ControlifyEvents;
import dev.isxander.controlify.bindings.BindContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.joml.Vector2d;

public class ControlifyCompat implements ControlifyEntrypoint {
    public static InputBindingSupplier RIDING_UP;
    public static InputBindingSupplier RIDING_DOWN;
    public static InputBindingSupplier RIDING_RIGHT;
    public static InputBindingSupplier RIDING_LEFT;

    public static final BindContext RIDING_POKEMON = new BindContext(
         MiscUtilsKt.cobblemonResource("riding"),
         mc -> {
             LocalPlayer localPlayer = mc.player;
             if (localPlayer == null) return false;
             Entity vehicle = localPlayer.getVehicle();
             return !(vehicle instanceof PokemonEntity);
         }
    );

    @Override
    public void onControlifyInit(ControlifyApi controlifyApi) {
        var bindings = ControlifyBindApi.get();
        bindings.registerBindContext(RIDING_POKEMON);

        RIDING_UP = bindings.registerBinding(builder -> builder
                .id(MiscUtilsKt.cobblemonResource("riding_up"))
                .category(Component.translatable("controlify.category.cobblemon.cobblemon"))
                .name(Component.translatable("controlify.bind.cobblemon.riding_up"))
                .allowedContexts(RIDING_POKEMON, BindContext.IN_GAME)
                //.addKeyCorrelation(ModKeybindings.PITCH_UP)
        );
        RIDING_DOWN = bindings.registerBinding(builder -> builder
                .id(MiscUtilsKt.cobblemonResource("riding_down"))
                .category(Component.translatable("controlify.category.cobblemon.cobblemon"))
                .name(Component.translatable("controlify.bind.cobblemon.riding_down"))
                .allowedContexts(RIDING_POKEMON, BindContext.IN_GAME)
                //.addKeyCorrelation(ModKeybindings.PITCH_DOWN)
        );
        RIDING_LEFT = bindings.registerBinding(builder -> builder
                .id(MiscUtilsKt.cobblemonResource("riding_left"))
                .category(Component.translatable("controlify.category.cobblemon.cobblemon"))
                .name(Component.translatable("controlify.bind.cobblemon.riding_left"))
                .allowedContexts(RIDING_POKEMON, BindContext.IN_GAME)
                //.addKeyCorrelation(ModKeybindings.ROLL_LEFT)
        );
        RIDING_RIGHT = bindings.registerBinding(builder -> builder
                .id(MiscUtilsKt.cobblemonResource("riding_right"))
                .category(Component.translatable("controlify.category.cobblemon.cobblemon"))
                .name(Component.translatable("controlify.bind.cobblemon.riding_right"))
                .allowedContexts(RIDING_POKEMON, BindContext.IN_GAME)
                //.addKeyCorrelation(ModKeybindings.ROLL_RIGHT)
        );

        ControlifyEvents.LOOK_INPUT_MODIFIER.register(event -> {
            LocalPlayer localPlayer = Minecraft.getInstance().player;
            if (localPlayer == null) return;
            Entity vehicle = localPlayer.getVehicle();
            if (vehicle instanceof PokemonEntity) event.lookInput().zero();;
        });

        CobblemonClient.INSTANCE.getRidingTurnInputHandler().put(MiscUtilsKt.cobblemonResource("compat.controlify"), ((localPlayer, xDelta, yDelta, deltaTime) -> {
            Vector2d vec = new Vector2d(xDelta, yDelta);
            var maybeController = controlifyApi.getCurrentController();
            if (localPlayer.getVehicle() instanceof PokemonEntity && maybeController.isPresent()) {
               var controller = maybeController.get();
               double multiplier = deltaTime * 1200;
               double vSensitivity = 1; // TODO: make config var
               double hSensitivity = 1; // TODO: make config var

               double vAxis = RIDING_UP.on(controller).analogueNow() - RIDING_DOWN.on(controller).analogueNow();
               double hAxis = RIDING_RIGHT.on(controller).analogueNow() - RIDING_LEFT.on(controller).analogueNow();

               vAxis *= multiplier * vSensitivity;
               hAxis *= multiplier * hSensitivity;
               vec.x = hAxis;
               vec.y = vAxis;
            }
            return vec;
        }));
    }

    @Override
    public void onControllersDiscovered(ControlifyApi controlifyApi) {
    }
}
