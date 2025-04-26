/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.OrientationControllable;
import com.cobblemon.mod.common.api.orientation.OrientationController;
import com.cobblemon.mod.common.api.riding.Seat;
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate;
import com.cobblemon.mod.common.client.render.MatrixWrapper;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin extends AbstractClientPlayer implements OrientationControllable {
    @Shadow
    private float jumpRidingScale;

    public LocalPlayerMixin(ClientLevel clientLevel, GameProfile gameProfile) {
        super(clientLevel, gameProfile);
    }

    @Inject(method = "getJumpRidingScale", at = @At("HEAD"), cancellable = true)
    public void modifyJumpRidingScale(CallbackInfoReturnable<Float> cir) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (player.isPassenger() && player.getVehicle() instanceof PokemonEntity pokemonEntity) {
            var rideValue = pokemonEntity.<Float>ifRidingAvailableSupply(null, (behaviour, settings, state) -> {
                if (behaviour.canJump(settings, state, pokemonEntity, player)) return null;
                return behaviour.setRideBar(settings, state, pokemonEntity, player);
            });
            if (rideValue != null) {
                cir.setReturnValue(rideValue);
            }
        }
    }

    @Override
    public HitResult pick(double hitDistance, float partialTicks, boolean hitFluids) {
        Entity vehicle = this.getVehicle();
        if (vehicle instanceof PokemonEntity pokemonEntity) {
            int seatIndex = pokemonEntity.getPassengers().indexOf(this);
            Seat seat = pokemonEntity.getSeats().get(seatIndex);

            PokemonClientDelegate delegate = (PokemonClientDelegate) pokemonEntity.getDelegate();
            MatrixWrapper locator = delegate.getLocatorStates().get(seat.getLocator());

            if (locator == null) {
                super.pick(hitDistance, partialTicks, hitFluids);
            }

            Vec3 locatorOffset = new Vec3(locator.getMatrix().getTranslation(new Vector3f()));

            OrientationController controller = this.getOrientationController();

            float currEyeHeight = this.getEyeHeight();
            Vector3f offset = new Vector3f(new Vector3f(0f, currEyeHeight - (this.getBbHeight() / 2), 0f));

            if (Minecraft.getInstance().options.getCameraType() == CameraType.FIRST_PERSON) {
                PosableModel model = VaryingModelRepository.INSTANCE.getPoser(pokemonEntity.getPokemon().getSpecies().getResourceIdentifier(), new FloatingState());
                offset.add(cobblemon$getFirstPersonOffset(model, seat));
            }

            Matrix3f orientation = controller.isActive() && controller.getOrientation() != null ? controller.getOrientation() : new Matrix3f();
            Vec3 rotatedEyeHeight = new Vec3(orientation.transform(offset));

            Vec3 eyePosition = locatorOffset.add(pokemonEntity.position()).add(rotatedEyeHeight);

            Vec3 viewVector = this.getViewVector(partialTicks);
            Vec3 viewDistanceVector = eyePosition.add(viewVector.x * hitDistance, viewVector.y * hitDistance, viewVector.z * hitDistance);
            return this.level()
                    .clip(
                            new ClipContext(
                                    eyePosition, viewDistanceVector, ClipContext.Block.OUTLINE, hitFluids ? net.minecraft.world.level.ClipContext.Fluid.ANY : net.minecraft.world.level.ClipContext.Fluid.NONE, this
                            )
                    );
        }

        return super.pick(hitDistance, partialTicks, hitFluids);
    }

    @Unique
    private static @NotNull Vector3f cobblemon$getFirstPersonOffset(PosableModel model, Seat seat) {
        Map<String, Vec3> cameraOffsets = model.getFirstPersonCameraOffset();

        if (cameraOffsets.containsKey(seat.getLocator())) {
            return cameraOffsets.get(seat.getLocator()).toVector3f();
        } else {
            return new Vector3f(0f, 0f, 0f);
        }
    }
}
