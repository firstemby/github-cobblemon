/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.duck.MinecraftServerDuck;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;
import java.util.Optional;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin implements MinecraftServerDuck {

	@Shadow
	@Final
	private Map<ResourceKey<Level>, ServerLevel> levels;

	@Override
	public Optional<ResourceKey<Level>> cobblemon$getLevelKey(final Level level) {
		return this.levels.entrySet()
				.stream()
				// Reference equality is intended here, the level we're handed is the same one as in the
				// map so we don't need to check value semantics.
				.filter(entry -> entry.getValue() == level)
				.map(Map.Entry::getKey)
				.findFirst();
	}


	/**
	 * Wraps the earliest {@code compositeAccess()} call in {@link MinecraftServer}'s constructor
	 * so Cobblemon can capture the server registry access as soon as it exists.
	 *
	 * <p>This sets {@link Cobblemon#registryAccess} before downstream initialization
	 * depends on biome lookups, while still delegating to the original call unchanged.</p>
	 *
	 * @author MeAlam
	 * @since April 9th 2026
	 */
	@WrapOperation(method = "<init>", at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/core/LayeredRegistryAccess;compositeAccess()Lnet/minecraft/core/RegistryAccess$Frozen;",
			ordinal = 0
	))
	private RegistryAccess.Frozen cobblemon$captureRegistryAccess
	(LayeredRegistryAccess<RegistryLayer> instance, Operation<RegistryAccess.Frozen> original) {
		Cobblemon.registryAccess = instance.compositeAccess();

		return original.call(instance);
	}
}
