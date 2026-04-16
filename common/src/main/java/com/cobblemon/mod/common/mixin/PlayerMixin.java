/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.OrientationControllable;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.item.LeftoversCreatedEvent;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.orientation.OrientationController;
import com.cobblemon.mod.common.api.riding.RidingStyle;
import com.cobblemon.mod.common.api.riding.behaviour.types.air.HoverBehaviour;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.api.tags.CobblemonItemTags;
import com.cobblemon.mod.common.duck.PlayerDuck;
import com.cobblemon.mod.common.duck.RidePassenger;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokedex.scanner.PokedexEntityData;
import com.cobblemon.mod.common.pokedex.scanner.ScannableEntity;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.util.CompoundTagExtensionsKt;
import com.cobblemon.mod.common.util.CompoundTagUtilities;
import com.cobblemon.mod.common.util.DataKeys;
import com.cobblemon.mod.common.util.ShoulderEntitySynchronizer;
import com.cobblemon.mod.common.world.gamerules.CobblemonGameRules;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity implements ScannableEntity, OrientationControllable, PlayerDuck, RidePassenger {
    @Unique
    private CompoundTag cobblemon$shoulderEntityLeft = new CompoundTag();
    @Unique
    private CompoundTag cobblemon$shoulderEntityRight = new CompoundTag();

    @Unique
    private Vector3f cobblemon$driverInput;
    @Unique
    private Vector3f cobblemon$lastSentDriverInput;

    @Shadow public abstract boolean isSpectator();

    @Shadow public abstract boolean addItem(ItemStack stack);

    @Shadow protected int jumpTriggerTime;
    @Unique private final OrientationController cobblemon$orientationController = new OrientationController(this);

    @Unique private float cobblemon$rideXRot = 0.0f;

    @Unique private float cobblemon$rideYRot = 0.0f;

    @Unique private Vec3 cobblemon$rideEyePos = Vec3.ZERO;

    protected PlayerMixin(EntityType<? extends LivingEntity> p_20966_, Level p_20967_) {
        super(p_20966_, p_20967_);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void cobblemon$readShoulderData(ValueInput input, CallbackInfo ci) {
        input.read("ShoulderEntityLeft", CompoundTag.CODEC)
            .ifPresent(entityNbt -> this.cobblemon$setShoulderEntity(true, entityNbt, false));
        input.read("ShoulderEntityRight", CompoundTag.CODEC)
            .ifPresent(entityNbt -> this.cobblemon$setShoulderEntity(false, entityNbt, false));
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void cobblemon$writeShoulderData(ValueOutput output, CallbackInfo ci) {
        CompoundTag leftShoulder = this.getShoulderEntityLeft();
        if (!leftShoulder.isEmpty()) {
            output.store("ShoulderEntityLeft", CompoundTag.CODEC, leftShoulder);
        }

        CompoundTag rightShoulder = this.getShoulderEntityRight();
        if (!rightShoulder.isEmpty()) {
            output.store("ShoulderEntityRight", CompoundTag.CODEC, rightShoulder);
        }
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        if (this.getVehicle() instanceof PokemonEntity) {
            return super.getDefaultDimensions(Pose.STANDING);
        }
        return super.getDefaultDimensions(pose);
    }

    @Override
    public CompoundTag getShoulderEntityLeft() {
        return this.cobblemon$shoulderEntityLeft;
    }

    @Override
    public CompoundTag getShoulderEntityRight() {
        return this.cobblemon$shoulderEntityRight;
    }

    @Override
    public void setShoulderEntityLeft(CompoundTag entityNbt) {
        this.cobblemon$setShoulderEntity(true, entityNbt, true);
    }

    @Override
    public void setShoulderEntityRight(CompoundTag entityNbt) {
        this.cobblemon$setShoulderEntity(false, entityNbt, true);
    }

    @Unique
    private void cobblemon$setShoulderEntity(boolean isLeftShoulder, CompoundTag entityNbt, boolean shouldSync) {
        CompoundTag copied = entityNbt == null ? new CompoundTag() : entityNbt.copy();
        if (isLeftShoulder) {
            this.cobblemon$shoulderEntityLeft = copied;
        } else {
            this.cobblemon$shoulderEntityRight = copied;
        }

        if (shouldSync && !this.level().isClientSide() && (Object)this instanceof ServerPlayer player) {
            ShoulderEntitySynchronizer.sync(player);
        }
    }


    @Redirect(
            method = "getDestroySpeed",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;onGround()Z"
            )
    )
    private boolean cobblemon$considerHoverOnGround(Player player) {
        // If riding a Hover Mount, return true to bypass the 5x slowdown
        if (player.getVehicle() instanceof PokemonEntity vehicle) {

            var ridingController = vehicle.getRidingController();
            if (ridingController == null) player.onGround();

            var activeContext = ridingController.getContext();
            if (activeContext == null) player.onGround();

            if (activeContext.getStyle() == RidingStyle.AIR &&
                activeContext.getBehaviour().equals(HoverBehaviour.Companion.getKEY())) {
                return true;
            }
        }
        // Otherwise, return the actual value
        return player.onGround();
    }


    @Inject(method = "isInvulnerableTo", at = @At("HEAD"), cancellable = true)
    public void isInvulnerableTo(net.minecraft.server.level.ServerLevel serverLevel, DamageSource source, CallbackInfoReturnable<Boolean> ci) {
        if ((Object) this instanceof ServerPlayer player) {
            boolean invulnerableInBattle = serverLevel.getGameRules().get(CobblemonGameRules.BATTLE_INVULNERABILITY);
            boolean inBattle = Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(player) != null;
            if (invulnerableInBattle && inBattle) {
                ci.setReturnValue(true);
            }
        }
    }

    @Override @Nullable
    public PokedexEntityData resolvePokemonScan() {
        if (CompoundTagUtilities.isShoulderPokemon(this.getShoulderEntityRight())) {
            return getDataFromShoulderPokemon(this.getShoulderEntityRight());
        }
        if (CompoundTagUtilities.isShoulderPokemon(this.getShoulderEntityLeft())) {
            return getDataFromShoulderPokemon(this.getShoulderEntityLeft());
        }
        return null;
    }

    @Nullable
    @Unique
    private PokedexEntityData getDataFromShoulderPokemon(CompoundTag shoulderTag) {
        Pokemon pokemon;
        if (level().isClientSide()) {
            CompoundTag pokemonTag = shoulderTag.getCompound(DataKeys.POKEMON).orElse(new CompoundTag());
            if (pokemonTag.isEmpty()) return null;
            String speciesId = pokemonTag.getString(DataKeys.POKEMON_SPECIES_IDENTIFIER).orElse("");
            if (speciesId.isEmpty()) return null;
            Species species = PokemonSpecies.getByIdentifier(Identifier.parse(speciesId));
            if (species == null) return null;
            String formId = pokemonTag.getString(DataKeys.POKEMON_FORM_ID).orElse("");
            FormData form = species.getStandardForm();
            List<FormData> formList = species.getForms().stream().filter(it -> it.formOnlyShowdownId().equals(formId)).toList();
            if (!formList.isEmpty()) form = formList.getFirst();
            if (form == null) return null;
            String genderString = pokemonTag.getString(DataKeys.POKEMON_GENDER).orElse("");
            if (genderString.isEmpty()) return null;
            Gender gender = Gender.valueOf(genderString);
            boolean shiny = pokemonTag.getBoolean(DataKeys.POKEMON_SHINY).orElse(false);
            int level = pokemonTag.getInt(DataKeys.POKEMON_LEVEL).orElse(0);
            Set<String> aspects = new HashSet<>();
            for (var tag : shoulderTag.getList(DataKeys.SHOULDER_ASPECTS).orElse(new ListTag())) {
                tag.asString().ifPresent(aspects::add);
            }

            pokemon = new Pokemon();
            pokemon.setSpecies(species);
            pokemon.setForm(form);
            pokemon.setGender(gender);
            pokemon.setShiny(shiny);
            pokemon.setLevel(level);
            pokemon.setForcedAspects(aspects);
        } else {
            PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(this.getUUID(), this.registryAccess());
            pokemon = party.get(CompoundTagExtensionsKt.getUUID(shoulderTag, DataKeys.SHOULDER_UUID));
        }
        return (pokemon == null) ? null : new PokedexEntityData(pokemon, null);
    }

    @Override
    public LivingEntity resolveEntityScan() {
        return this;
    }

    @WrapOperation(
            method = "rideTick()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;wantsToStopRiding()Z")
    )
    public boolean delegateDismountToController(Player instance, Operation<Boolean> original) {
        if (this.getVehicle() instanceof PokemonEntity pokemonEntity) {
            return pokemonEntity.ifRidingAvailableSupply(false, (behaviour, settings, state) -> {
                return behaviour.dismountOnShift(settings, state, pokemonEntity) && original.call(instance);
            });
        }
        return original.call(instance);
    }

    @Override
    public OrientationController getOrientationController() {
        return cobblemon$orientationController;
    }

    @Override
    public void setDriverInput(Vector3f driverInput) {
        cobblemon$driverInput = driverInput;
    }

    @Override
    public Vector3f getDriverInput() {
        return cobblemon$driverInput;
    }

    @Override
    public void setLastSentDriverInput(Vector3f lastSentDriverInput) {
        cobblemon$lastSentDriverInput = lastSentDriverInput;
    }

    @Override
    public Vector3f getLastSentDriverInput() {
        return cobblemon$lastSentDriverInput;
    }

    @Override
    public float cobblemon$getRideXRot() {
        return this.cobblemon$rideXRot;
    }

    @Override
    public void cobblemon$setRideXRot(float rideXRot) {
        this.cobblemon$rideXRot = Mth.wrapDegrees(rideXRot);
    }

    @Override
    public float cobblemon$getRideYRot() {
        return this.cobblemon$rideYRot;
    }

    @Override
    public void cobblemon$setRideYRot(float rideYRot) {
        this.cobblemon$rideYRot = Mth.wrapDegrees(rideYRot);
    }

    @Override
    public Vec3 cobblemon$getRideEyePos() {
        return this.cobblemon$rideEyePos;
    }

    @Override
    public void cobblemon$setRideEyePos(Vec3 rideEyePos) {
        this.cobblemon$rideEyePos = rideEyePos;
    }

}
