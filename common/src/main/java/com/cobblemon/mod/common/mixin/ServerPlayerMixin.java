/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.OrientationControllable;
import com.cobblemon.mod.common.PlayerSpawnerAccessor;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.riding.RidingStyle;
import com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawner;
import com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawnerFactory;
import com.cobblemon.mod.common.api.stats.CobblemonStats;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
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
import com.cobblemon.mod.common.world.gamerules.CobblemonGameRules;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Dynamic;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin extends Player implements PlayerSpawnerAccessor, ScannableEntity {

    public ServerPlayerMixin(Level level, GameProfile gameProfile) {
        super(level, gameProfile);
    }

    @Shadow public abstract CompoundTag getShoulderEntityLeft();

    @Shadow public abstract CompoundTag getShoulderEntityRight();

    @Shadow public abstract void respawnEntityOnShoulder(CompoundTag entityNbt);

    @Shadow public abstract void setShoulderEntityRight(CompoundTag entityNbt);

    @Shadow public abstract void setShoulderEntityLeft(CompoundTag entityNbt);

    @Shadow
    public abstract ServerLevel level();

    @Unique
    public PlayerSpawner cobblemon$spawner;

    @Override
    public PlayerSpawner getPlayerSpawner() {
        ServerPlayer player = (ServerPlayer)(Object)this;
        if (cobblemon$spawner == null) {
            cobblemon$spawner = PlayerSpawnerFactory.INSTANCE.create(player);
        }
        return cobblemon$spawner;
    }

    @Override
    public void setPlayerSpawner(PlayerSpawner spawner) {
        this.cobblemon$spawner = spawner;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void cobblemon$tickSpawner(CallbackInfo ci) {
        if (!Cobblemon.config.getEnableSpawning()) {
            return;
        } else if (!level().getGameRules().get(CobblemonGameRules.DO_POKEMON_SPAWNING)) {
            return;
        }
        getPlayerSpawner().tick();
    }

    @Inject(method = "rideTick", at = @At("HEAD"))
    private void cobblemon$updateOrientationControllerRideTick(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer)(Object)this;
        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof OrientationControllable controllableVehicle)) return;
        var shouldUseCustomOrientation = cobblemon$shouldUseCustomOrientation(vehicle);
        controllableVehicle.getOrientationController().setActive(shouldUseCustomOrientation);
    }

    @Unique
    private boolean cobblemon$shouldUseCustomOrientation(Entity entity) {
        if (entity == null) return false;
        if (!(entity instanceof PokemonEntity pokemonEntity)) return false;
        return pokemonEntity.ifRidingAvailableSupply(false, (behaviour, settings, state) -> {
            return behaviour.shouldRoll(settings, state, pokemonEntity);
        });
    }

    //TODO: Switch to sending out on load instead of preventing saving
    @ModifyExpressionValue(method = "saveParentVehicle", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hasExactlyOnePlayerPassenger()Z"))
    private boolean cobblemon$cancelSavingPokemonMounts(boolean original, @Local(ordinal=0) Entity entity) {
        return original && !(entity instanceof PokemonEntity);
    }

    @WrapOperation(method = "checkRidingStatistics", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getVehicle()Lnet/minecraft/world/entity/Entity;"))
    public Entity cobblemon$checkRidingStatistics(ServerPlayer player, Operation<Entity> original, @Local int distance) {
        var entity = original.call(player);
        if (!(entity instanceof PokemonEntity pokemonEntity)) {
            return entity;
        }

        var ridingController = pokemonEntity.getRidingController();
        if (ridingController == null) return entity;

        var activeContext = ridingController.getContext();
        if (activeContext == null) return entity;

        var ridingStyle = activeContext.getStyle();
        CobblemonStats.CobblemonStat stat = switch (ridingStyle) {
            case RidingStyle.LAND -> CobblemonStats.RIDING_LAND;
            case RidingStyle.AIR -> CobblemonStats.RIDING_AIR;
            case RidingStyle.LIQUID -> CobblemonStats.RIDING_LIQUID;
            default -> null;
        };

        if (stat != null) {
            player.awardStat(CobblemonStats.getStat(stat), distance);
        }

        return entity;
    }

    @Inject(method = "respawnEntityOnShoulder", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/EntityType;create(Lnet/minecraft/world/level/storage/ValueInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntitySpawnReason;)Ljava/util/Optional;"),
            cancellable = true)
    private void cobblemon$removePokemon(CompoundTag nbt, CallbackInfo ci) {
        if (CompoundTagExtensionsKt.isPokemonEntity(nbt)) {
            final UUID uuid = CompoundTagUtilities.getPokemonID(nbt);
            if (CompoundTagUtilities.isShoulderPokemon(this.getShoulderEntityRight())) {
                final UUID uuidRight = CompoundTagUtilities.getPokemonID(this.getShoulderEntityRight());
                if (uuid.equals(uuidRight)) {
                    this.recallPokemon(uuidRight);
                    this.setShoulderEntityRight(new CompoundTag());
                }
            }
            if (CompoundTagUtilities.isShoulderPokemon(this.getShoulderEntityLeft())) {
                final UUID uuidLeft = CompoundTagUtilities.getPokemonID(this.getShoulderEntityLeft());
                if (uuid.equals(uuidLeft)) {
                    this.recallPokemon(uuidLeft);
                    this.setShoulderEntityLeft(new CompoundTag());
                }
            }
            ci.cancel();
        }
    }

    @Inject(
            method = "removeEntitiesOnShoulder",
            at = @At(
                    value = "JUMP",
                    opcode = Opcodes.IFGE,
                    ordinal = 0,
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void cobblemon$preventPokemonDropping(CallbackInfo ci) {
        // We want to allow both of these to forcefully remove the entities
        if (this.isSpectator() || this.isDeadOrDying())
            return;
        if (!CompoundTagUtilities.isShoulderPokemon(this.getShoulderEntityLeft())) {
            this.respawnEntityOnShoulder(this.getShoulderEntityLeft());
            this.setShoulderEntityLeft(new CompoundTag());
        }
        if (!CompoundTagUtilities.isShoulderPokemon(this.getShoulderEntityRight())) {
            this.respawnEntityOnShoulder(this.getShoulderEntityRight());
            this.setShoulderEntityRight(new CompoundTag());
        }
        ci.cancel();
    }

    private void recallPokemon(UUID uuid) {
        // We need to do this cause the Entity doesn't store a reference to its storage
        final PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(this.getUUID(), this.registryAccess());
        for (Pokemon pokemon : party) {
            if (pokemon.getUuid().equals(uuid)) {
                pokemon.recall();
            }
        }
    }

    @Override @Nullable
    public PokedexEntityData resolvePokemonScan() {
        if (CompoundTagUtilities.isShoulderPokemon(this.getShoulderEntityRight())){
            return getDataFromShoulderPokemon(this.getShoulderEntityRight());
        }
        if (CompoundTagUtilities.isShoulderPokemon(this.getShoulderEntityLeft())){
            return getDataFromShoulderPokemon(this.getShoulderEntityLeft());
        }
        return null;
    }

    @Nullable @Unique
    private PokedexEntityData getDataFromShoulderPokemon(CompoundTag shoulderTag) {
        Pokemon pokemon;
        if (level().isClientSide()) {
            var pokemonTagOpt = shoulderTag.getCompound(DataKeys.POKEMON);
            if (pokemonTagOpt.isEmpty()) return null;
            var pokemonTag = pokemonTagOpt.get();

            var speciesOpt = pokemonTag.getString(DataKeys.POKEMON_SPECIES_IDENTIFIER);
            if(speciesOpt.isEmpty())
                return null;
            Species species = PokemonSpecies.getByIdentifier(Identifier.parse(speciesOpt.get()));
            if (species == null) return null;
            String formId = pokemonTag.getString(DataKeys.POKEMON_FORM_ID).get();
            FormData form = species.getStandardForm();
            List<FormData> formList = species.getForms().stream().filter(it -> it.formOnlyShowdownId().equals(formId)).toList();
            if (!formList.isEmpty()) form = formList.getFirst();
            if (form == null) return null;
            var genderString = pokemonTag.getString(DataKeys.POKEMON_GENDER);
            if (genderString.isEmpty()) return null;
            Gender gender = Gender.valueOf(genderString.get());
            boolean shiny = pokemonTag.getBooleanOr(DataKeys.POKEMON_SHINY, false);
            int level = pokemonTag.getInt(DataKeys.POKEMON_LEVEL).get();
            Set<String> aspects = shoulderTag.getList(DataKeys.SHOULDER_ASPECTS).stream().map(Tag::asString).map(Optional::get).collect(Collectors.toSet());

            pokemon = new Pokemon();
            pokemon.setSpecies(species);
            pokemon.setForm(form);
            pokemon.setGender(gender);
            pokemon.setShiny(shiny);
            pokemon.setLevel(level);
            pokemon.setForcedAspects(aspects);
        } else {
            PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(this.getUUID(), this.registryAccess());

            pokemon = party.get(UUIDUtil.readUUID(new Dynamic(NbtOps.INSTANCE, shoulderTag.get(DataKeys.SHOULDER_UUID))));
        }
        return (pokemon == null) ? null : new PokedexEntityData(pokemon, null);
    }

    @Override
    public LivingEntity resolveEntityScan() {
        return this;
    }
}
