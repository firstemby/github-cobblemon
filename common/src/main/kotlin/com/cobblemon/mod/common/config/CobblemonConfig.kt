/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.config

import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.api.breeding.EggTextureStyle
import com.cobblemon.mod.common.api.drop.ItemDropMethod
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.api.pokeball.catching.calculators.CaptureCalculator
import com.cobblemon.mod.common.api.pokemon.status.Statuses
import com.cobblemon.mod.common.client.gui.PokemonGUIAnimationStyle
import com.cobblemon.mod.common.config.CobblemonConfigField.CobblemonConfigSide.CLIENT
import com.cobblemon.mod.common.config.CobblemonConfigField.CobblemonConfigSide.SERVER
import com.cobblemon.mod.common.config.constraint.IntConstraint
import com.cobblemon.mod.common.pokeball.catching.calculators.CobblemonCaptureCalculator
import com.cobblemon.mod.common.util.adapters.CaptureCalculatorAdapter
import com.cobblemon.mod.common.util.adapters.IdentifierAdapter
import com.cobblemon.mod.common.util.adapters.IntRangeAdapter
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import net.minecraft.resources.ResourceLocation

class CobblemonConfig {
    companion object {
        val GSON = GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .registerTypeAdapter(IntRange::class.java, IntRangeAdapter)
            .registerTypeAdapter(ItemDropMethod::class.java, ItemDropMethod.adapter)
            .registerTypeAdapter(CaptureCalculator::class.java, CaptureCalculatorAdapter)
            .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
            .create()
    }

    var lastSavedVersion: String = "0.0.1"

    @CobblemonConfigField(Category.Pokemon, lang = "max_pokemon_level", SERVER)
    @IntConstraint(min = 1, max = 1000)
    var maxPokemonLevel = 100

    @CobblemonConfigField(Category.Pokemon, lang = "max_pokemon_friendship", SERVER)
    @IntConstraint(min = 0, max = 1000)
    var maxPokemonFriendship = 255

    @CobblemonConfigField(Category.Pokemon, lang = "announce_drop_items", SERVER)
    var announceDropItems = true
    @CobblemonConfigField(Category.Pokemon, lang = "default_drop_item_method", SERVER)
    var defaultDropItemMethod = ItemDropMethod.ON_ENTITY
    @CobblemonConfigField(Category.Pokemon, lang = "drops_after_death_animation", SERVER)
    var dropAfterDeathAnimation = false

    @CobblemonConfigField(Category.Pokemon, lang = "ambient_pokemon_cry_ticks", SERVER)
    @LastChangedVersion("1.4.0")
    var ambientPokemonCryTicks = 1080

    @CobblemonConfigField(Category.Storage, lang = "default_key_items", SERVER)
    var defaultKeyItems = mutableSetOf<ResourceLocation>()

    @CobblemonConfigField(Category.Storage, lang = "default_box_count", SERVER)
    @IntConstraint(min = 1, max = 1000)
    @LastChangedVersion("1.7.0")
    var defaultBoxCount = 40
    @CobblemonConfigField(Category.Storage, lang = "pokemon_save_interval_seconds", SERVER)
    @IntConstraint(min = 1, max = 120)
    var pokemonSaveIntervalSeconds = 30

    @CobblemonConfigField(Category.Storage, lang = "storage_format", SERVER)
    var storageFormat = "nbt"

    @CobblemonConfigField(Category.Storage, lang = "prevent_complete_party_deposit", SERVER)
    var preventCompletePartyDeposit = false

    @CobblemonConfigField(Category.Storage, lang = "mongo_db_connection_string", SERVER)
    var mongoDBConnectionString = "mongodb://localhost:27017"
    @CobblemonConfigField(Category.Storage, lang = "mongo_db_database_name", SERVER)
    var mongoDBDatabaseName = "cobblemon"

    @CobblemonConfigField(Category.Spawning, lang = "max_vertical_correction_blocks", SERVER)
    @IntConstraint(min = 1, max = 200)
    var maxVerticalCorrectionBlocks = 64

    @CobblemonConfigField(Category.Spawning, lang = "minimum_level_range_max", SERVER)
    @IntConstraint(min = 1, max = 1000)
    var minimumLevelRangeMax = 10

    @CobblemonConfigField(Category.Spawning, lang = "enable_spawning", SERVER)
    var enableSpawning = true

    @CobblemonConfigField(Category.Spawning, lang = "world_spawning_blocklist", SERVER)
    var worldSpawningBlocklist = mutableSetOf<ResourceLocation>()

    @CobblemonConfigField(Category.Spawning, lang = "minimum_distance_between_entities", SERVER)
    var minimumDistanceBetweenEntities = 8.0

    @CobblemonConfigField(Category.Spawning, lang = "max_nearby_blocks_horizontal_range", SERVER)
    var maxNearbyBlocksHorizontalRange = 4

    @CobblemonConfigField(Category.Spawning, lang = "max_nearby_blocks_vertical_range", SERVER)
    var maxNearbyBlocksVerticalRange = 2

    @CobblemonConfigField(Category.Spawning, lang = "max_vertical_space", SERVER)
    var maxVerticalSpace = 8

    @CobblemonConfigField(Category.Spawning, lang = "spawning_zone_diameter", SERVER)
    @SerializedName("spawningZoneDiameter", alternate = ["worldSliceDiameter"])
    var spawningZoneDiameter = 8

    @CobblemonConfigField(Category.Spawning, lang = "spawning_zone_height", SERVER)
    @SerializedName("spawningZoneHeight", alternate = ["worldSliceHeight"])
    var spawningZoneHeight = 16

    @CobblemonConfigField(Category.Spawning, lang = "ticks_between_spawn_attempts", SERVER)
    var ticksBetweenSpawnAttempts = 20F

    @CobblemonConfigField(Category.Spawning, lang = "minimum_spawning_zone_distance_from_player", SERVER)
    @SerializedName("minimumSpawningZoneDistanceFromPlayer", alternate = ["minimumSliceDistanceFromPlayer"])
    var minimumSpawningZoneDistanceFromPlayer = 16F

    @CobblemonConfigField(Category.Spawning, lang = "maximum_spawning_zone_distance_from_player", SERVER)
    @SerializedName("maximumSpawningZoneDistanceFromPlayer", alternate = ["maximumSliceDistanceFromPlayer"])
    var maximumSpawningZoneDistanceFromPlayer = 16 * 4F

    @CobblemonConfigField(Category.Spawning, lang = "maximum_spawns_per_pass", SERVER)
    var maximumSpawnsPerPass = 8

    @CobblemonConfigField(Category.Spawning, lang = "export_spawn_config", SERVER)
    var exportSpawnConfig = false

    @CobblemonConfigField(Category.Spawning, lang = "save_pokemon_to_world", SERVER)
    var savePokemonToWorld = true

    @CobblemonConfigField(Category.Starter, lang = "export_starter_config", SERVER)
    var exportStarterConfig = false

    @CobblemonConfigField(Category.Battles, lang = "auto_update_showdown", SERVER)
    var autoUpdateShowdown = true

    @CobblemonConfigField(Category.Battles, lang = "default_flee_distance", SERVER)
    var defaultFleeDistance = 16F * 2

    @CobblemonConfigField(Category.Battles, lang = "allow_experience_from_pvp", SERVER)
    var allowExperienceFromPvP = true

    @CobblemonConfigField(Category.Battles, lang = "experience_share_multiplier", SERVER)
    var experienceShareMultiplier = .5

    @CobblemonConfigField(Category.Battles, lang = "award_experience_to_fainted_pokemon", SERVER)
    var awardExperienceToFaintedPokemon = false

    @CobblemonConfigField(Category.Battles, lang = "award_experience_on_battle_loss", SERVER)
    var awardExperienceOnBattleLoss = false

    @CobblemonConfigField(Category.Battles, lang = "lucky_egg_multiplier", SERVER)
    var luckyEggMultiplier = 1.5

    @CobblemonConfigField(Category.Battles, lang = "allow_spectating", SERVER)
    var allowSpectating = true

    @CobblemonConfigField(Category.Pokemon, lang = "experience_multiplier", SERVER)
    var experienceMultiplier = 2F

    @CobblemonConfigField(Category.Spawning, lang = "pokemon_per_chunk", SERVER)
    var pokemonPerChunk = 1F

    @CobblemonConfigField(Category.Spawning, lang = "poke_snack_pokemon_per_chunk", SERVER)
    var pokeSnackPokemonPerChunk = 2F

    @CobblemonConfigField(Category.PassiveStatus, lang = "passive_statuses", SERVER)
    var passiveStatuses = mutableMapOf(
        Statuses.POISON.configEntry(),
        Statuses.POISON_BADLY.configEntry(),
        Statuses.PARALYSIS.configEntry(),
        Statuses.FROZEN.configEntry(),
        Statuses.SLEEP.configEntry(),
        Statuses.BURN.configEntry()
    )

    @CobblemonConfigField(Category.Healing, lang = "infinite_healer_charge", SERVER)
    var infiniteHealerCharge = false

    @CobblemonConfigField(Category.Healing, lang = "max_healer_charge", SERVER)
    var maxHealerCharge = 6.0f

    @CobblemonConfigField(Category.Healing, lang = "seconds_to_charge_healing_machine", SERVER)
    var secondsToChargeHealingMachine = 900.0

    @CobblemonConfigField(Category.Healing, lang = "default_faint_timer", SERVER)
    var defaultFaintTimer = 300

    @CobblemonConfigField(Category.Healing, lang = "faint_awaken_health_percent", SERVER)
    var faintAwakenHealthPercent = 0.2f

    @CobblemonConfigField(Category.Healing, lang = "heal_percent", SERVER)
    var healPercent = 0.05

    @CobblemonConfigField(Category.Healing, lang = "heal_timer", SERVER)
    var healTimer = 60

    @CobblemonConfigField(Category.Spawning, lang = "base_apricorn_tree_generation_chance", SERVER)
    var baseApricornTreeGenerationChance = 0.1F

    @CobblemonConfigField(Category.Pokemon, lang = "display_entity_level_label", CLIENT)
    var displayEntityLevelLabel = true

    @CobblemonConfigField(Category.Pokemon, lang = "display_entity_name_label", CLIENT)
    var displayEntityNameLabel = true

    @CobblemonConfigField(Category.Pokemon, lang = "display_name_for_unknown_pokemon", CLIENT)
    var displayNameForUnknownPokemon = false

    @CobblemonConfigField(Category.Pokemon, lang = "display_entity_labels_when_crouching_only", CLIENT)
    var displayEntityLabelsWhenCrouchingOnly = false

    @CobblemonConfigField(Category.Spawning, lang = "shiny_rate", SERVER)
    var shinyRate = 8192F

    @CobblemonConfigField(Category.Pokemon, lang = "fossil_machine_alpha_chance", SERVER)
    var fossilMachineAlphaChance = 20

    @CobblemonConfigField(Category.Pokemon, lang = "fossil_machine_shiny_chance", SERVER)
    var fossilMachineShinyChance = 100

    @CobblemonConfigField(Category.Pokemon, lang = "honey_slather_alpha_chance", SERVER)
    var honeySlatherAlphaChance = 100

    @CobblemonConfigField(Category.Pokemon, lang = "honey_slather_shiny_chance", SERVER)
    var honeySlatherShinyChance = 4000

    @CobblemonConfigField(Category.Pokemon, lang = "shiny_notice_particles_distance", CLIENT)
    var shinyNoticeParticlesDistance = 24F

    @CobblemonConfigField(Category.Pokemon, lang = "capture_calculator", SERVER)
    var captureCalculator: CaptureCalculator = CobblemonCaptureCalculator

    @CobblemonConfigField(Category.Pokemon, lang = "player_damage_pokemon", SERVER)
    var playerDamagePokemon = true

    @CobblemonConfigField(Category.Pokemon, lang = "pokemon_intrinsic_size_min", SERVER)
    var pokemonIntrinsicSizeMin = 0.95F

    @CobblemonConfigField(Category.Pokemon, lang = "pokemon_intrinsic_size_max", SERVER)
    var pokemonIntrinsicSizeMax = 1.05F

    @CobblemonConfigField(Category.Pokemon, lang = "baby_pokemon_level_duration", SERVER)
    var babyPokemonLevelDuration = 9

    @CobblemonConfigField(Category.Pokemon, lang = "baby_pokemon_size_multiplier", SERVER)
    var babyPokemonSizeMultiplier = 0.9F

    @CobblemonConfigField(Category.Pokemon, lang = "minimum_riding_scale", SERVER)
    var minimumRidingScale = 0.75F

    @CobblemonConfigField(Category.World, lang = "apple_leftovers_chance", SERVER)
    var appleLeftoversChance = 0.025

    @CobblemonConfigField(Category.World, lang = "max_roots_in_area", SERVER)
    @LastChangedVersion("1.7.0")
    var maxRootsInArea = 9

    @CobblemonConfigField(Category.World, lang = "big_root_propagation_chance", SERVER)
    @LastChangedVersion("1.7.0")
    var bigRootPropagationChance = 0.5

    @CobblemonConfigField(Category.World, lang = "energy_root_chance", SERVER)
    var energyRootChance = 0.25

    @CobblemonConfigField(Category.Pokemon, lang = "max_dynamax_level", SERVER)
    @IntConstraint(min = 0, max = 10)
    var maxDynamaxLevel = 10

    @CobblemonConfigField(Category.Spawning, lang = "tera_type_rate", SERVER)
    var teraTypeRate = 20F

    @CobblemonConfigField(Category.World, lang = "default_pastured_pokemon_limit", SERVER)
    var defaultPasturedPokemonLimit = 16

    @CobblemonConfigField(Category.World, lang = "pasture_block_update_ticks", SERVER)
    var pastureBlockUpdateTicks = 40

    @CobblemonConfigField(Category.World, lang = "pasture_max_wander_distance", SERVER)
    var pastureMaxWanderDistance = 32

    @CobblemonConfigField(Category.World, lang = "pasture_max_per_chunk", SERVER)
    var pastureMaxPerChunk = 4F

    @CobblemonConfigField(Category.World, lang = "max_inserted_fossil_items", SERVER)
    var maxInsertedFossilItems = 2

    @CobblemonConfigField(Category.Battles, lang = "walking_in_battle_animations", CLIENT)
    var walkingInBattleAnimations = false

    @CobblemonConfigField(Category.Battles, lang = "battle_wild_max_distance", SERVER)
    var battleWildMaxDistance = 12F

    @CobblemonConfigField(Category.World, lang = "trade_max_distance", SERVER)
    var tradeMaxDistance = 12F

    @CobblemonConfigField(Category.Battles, lang = "battle_pvp_max_distance", SERVER)
    @SerializedName("battlePvPMaxDistance", alternate = ["BattlePvPMaxDistance"])
    var battlePvPMaxDistance = 32F

    @CobblemonConfigField(Category.Battles, lang = "battle_spectate_max_distance", SERVER)
    var battleSpectateMaxDistance = 64F

    @CobblemonConfigField(Category.Pokedex, lang = "max_pokedex_scanning_detection_range", SERVER)
    var maxPokedexScanningDetectionRange = 10.0

    @CobblemonConfigField(Category.Pokedex, lang = "unlock_all_move_dex_moves_by_default", SERVER)
    var unlockAllMoveDexMovesByDefault = false

    @CobblemonConfigField(Category.Pokemon, lang = "infinite_tm_uses", SERVER)
    var infiniteTmUses = false

    @CobblemonConfigField(Category.Pokedex, lang = "hide_unimplemented_pokemon_in_the_pokedex", CLIENT)
    var hideUnimplementedPokemonInThePokedex = false

    @CobblemonConfigField(Category.Breeding, lang = "can_destiny_knot_break", side = SERVER)
    var canDestinyKnotBreak = true

    @CobblemonConfigField(Category.Breeding, lang = "requires_ots_for_masuda", side = SERVER)
    var requireOTsForMasuda = false

    @CobblemonConfigField(Category.Breeding, lang = "can_breed_genderless", side = SERVER)
    var canBreedGenderless = true

    @CobblemonConfigField(Category.Breeding, lang = "can_breed_undiscovered", side = SERVER)
    var canBreedUndiscovered = true

    @CobblemonConfigField(Category.Breeding, lang = "can_breed_ditto", side = SERVER)
    var canBreedDitto = true

    @CobblemonConfigField(Category.Breeding, lang = "egg_cycle_to_minute", side = SERVER)
    var minutesPerEggCycle = 2.0

    @CobblemonConfigField(Category.Breeding, lang = "nest_hatch_rate", side = SERVER)
    var nestHatchRate = 1F

    @CobblemonConfigField(Category.Breeding, lang = "incubator_hatch_rate", side = SERVER)
    var incubatorHatchRate = 1.25F

    @CobblemonConfigField(Category.Breeding, lang = "nether_nest_hatch_rate", side = SERVER)
    var netherNestHatchRate = 1.5F

    @CobblemonConfigField(Category.Breeding, lang = "nether_incubator_hatch_rate", side = SERVER)
    var netherIncubatorHatchRate = 1.75F

    @CobblemonConfigField(Category.Breeding, lang = "wild_breeding_chance", side = SERVER)
    var wildBreedingChance = 0.05

    @CobblemonConfigField(Category.Breeding, lang = "pasture_breeding_chance", side = SERVER)
    var pastureBreedingChance = 1.0

    @CobblemonConfigField(Category.Breeding, lang = "wild_breeding_cooldown_multiplier", side = SERVER)
    var wildBreedingCooldownMultiplier = 1.0

    @CobblemonConfigField(Category.Breeding, lang = "pasture_breeding_cooldown_multiplier", side = SERVER)
    var pastureBreedingCooldownMultiplier = 0.7

    @CobblemonConfigField(Category.Breeding, lang = "bench_extra_inherited_moves", side = SERVER)
    var benchExtraInheritedMoves = true

    @CobblemonConfigField(Category.Breeding, lang = "default_egg_texture_style", side = SERVER)
    var defaultEggTextureStyle = EggTextureStyle.SPECIES

    @CobblemonConfigField(Category.Interface, lang = "summary_pokemon_follow_cursor", CLIENT)
    var summaryPokemonFollowCursor = true

    @CobblemonConfigField(Category.Interface, lang = "party_portrait_animations", CLIENT)
    var partyPortraitAnimations = PokemonGUIAnimationStyle.NEVER_ANIMATE

    @CobblemonConfigField(Category.Interface, lang = "pc_profile_animations", CLIENT)
    var pcProfileAnimations = PokemonGUIAnimationStyle.ANIMATE_SELECTED

    @CobblemonConfigField(Category.Interface, lang = "summary_profile_animations", CLIENT)
    var summaryProfileAnimations = PokemonGUIAnimationStyle.ANIMATE_SELECTED

    @CobblemonConfigField(Category.Interface, lang = "animate_battle_tiles", CLIENT)
    var animateBattleTiles = false

    // Disabled as non-tenable for 1.7 until all the posers catch up with this property.
    // @CobblemonConfigField(Category.Riding, lang = "third_person_view_bobbing", CLIENT)
    var thirdPersonViewBobbing = true

    @CobblemonConfigField(Category.Riding, lang = "invert_pitch", CLIENT)
    var invertPitch = false

    @CobblemonConfigField(Category.Riding, lang = "invert_yaw", CLIENT)
    var invertYaw = false

    @CobblemonConfigField(Category.Riding, lang = "x_axis_sensitivity", CLIENT)
    var xAxisSensitivity = 1.0

    @CobblemonConfigField(Category.Riding, lang = "y_axis_sensitivity", CLIENT)
    var yAxisSensitivity = 1.0

    @CobblemonConfigField(Category.Riding, lang = "swap_x_and_y_axes", CLIENT)
    var swapXAndYAxes = false

    @CobblemonConfigField(Category.Riding, lang = "disable_roll", CLIENT)
    var disableRoll = false

    @CobblemonConfigField(Category.Riding, lang = "display_controls_duration_seconds", CLIENT)
    var displayControlSeconds = 0

    @CobblemonConfigField(Category.Riding, lang = "infinite_ride_stamina", SERVER)
    var infiniteRideStamina = false

    @CobblemonConfigField(Category.Riding, lang = "remember_riding_camera", CLIENT)
    var rememberRidingCamera = false

    @CobblemonConfigField(Category.Riding, lang = "enable_in_flight_dismounting", CLIENT)
    var enableInFlightDismounting = false

    @CobblemonConfigField(Category.Debug, lang = "enable_debug_keys", CLIENT)
    var enableDebugKeys = false

    @CobblemonConfigField(Category.Spawning, lang = "despawner_near_distance", SERVER)
    var despawnerNearDistance = 32f

    @CobblemonConfigField(Category.Spawning, lang = "despawner_far_distance", SERVER)
    var despawnerFarDistance = 96f

    @CobblemonConfigField(Category.Spawning, lang = "despawner_min_age_ticks", SERVER)
    var despawnerMinAgeTicks = 600

    @CobblemonConfigField(Category.Spawning, lang = "despawner_max_age_ticks", SERVER)
    var despawnerMaxAgeTicks = 3600

    @CobblemonConfigField(Category.Spawning, lang = "data_monitor_alpha_rate", SERVER)
    var monitorAlphaRate = 40

    @CobblemonConfigField(Category.Spawning, lang = "data_monitor_shiny_rate", SERVER)
    var monitorShinyRate = 200

    @CobblemonConfigField(Category.Pokemon, lang = "scarekrow_radius", SERVER)
    var scarekrowRadius = 16

    @Transient
    val struct = ObjectValue(this).also {
        it.addFunction("last_saved_version") { StringValue(lastSavedVersion) }
        it.addFunction("max_pokemon_level") { DoubleValue(maxPokemonLevel) }
        it.addFunction("max_pokemon_friendship") { DoubleValue(maxPokemonFriendship) }
        it.addFunction("announce_drop_items") { DoubleValue(announceDropItems) }
        it.addFunction("default_drop_item_method") { StringValue(defaultDropItemMethod.name.lowercase()) }
        it.addFunction("drop_after_death_animation") { DoubleValue(dropAfterDeathAnimation) }
        it.addFunction("ambient_pokemon_cry_ticks") { DoubleValue(ambientPokemonCryTicks) }
        it.addFunction("default_box_count") { DoubleValue(defaultBoxCount) }
        it.addFunction("pokemon_save_interval_seconds") { DoubleValue(pokemonSaveIntervalSeconds) }
        it.addFunction("storage_format") { StringValue(storageFormat) }
        it.addFunction("prevent_complete_party_deposit") { DoubleValue(preventCompletePartyDeposit) }
        it.addFunction("max_vertical_correction_blocks") { DoubleValue(maxVerticalCorrectionBlocks) }
        it.addFunction("minimum_level_range_max") { DoubleValue(minimumLevelRangeMax) }
        it.addFunction("enable_spawning") { DoubleValue(enableSpawning) }
        it.addFunction("minimum_distance_between_entities") { DoubleValue(minimumDistanceBetweenEntities) }
        it.addFunction("max_nearby_blocks_horizontal_range") { DoubleValue(maxNearbyBlocksHorizontalRange) }
        it.addFunction("max_nearby_blocks_vertical_range") { DoubleValue(maxNearbyBlocksVerticalRange) }
        it.addFunction("max_vertical_space") { DoubleValue(maxVerticalSpace) }
        it.addFunction("spawning_zone_diameter") { DoubleValue(spawningZoneDiameter) }
        it.addFunction("spawning_zone_height") { DoubleValue(spawningZoneHeight) }
        it.addFunction("ticks_between_spawn_attempts") { DoubleValue(ticksBetweenSpawnAttempts) }
        it.addFunction("minimum_spawning_zone_distance_from_player") { DoubleValue(minimumSpawningZoneDistanceFromPlayer) }
        it.addFunction("maximum_spawning_zone_distance_from_player") { DoubleValue(maximumSpawningZoneDistanceFromPlayer) }
        it.addFunction("maximum_spawns_per_pass") { DoubleValue(maximumSpawnsPerPass) }
        it.addFunction("export_spawn_config") { DoubleValue(exportSpawnConfig) }
        it.addFunction("save_pokemon_to_world") { DoubleValue(savePokemonToWorld) }
        it.addFunction("export_starter_config") { DoubleValue(exportStarterConfig) }
        it.addFunction("auto_update_showdown") { DoubleValue(autoUpdateShowdown) }
        it.addFunction("default_flee_distance") { DoubleValue(defaultFleeDistance) }
        it.addFunction("allow_experience_from_pvp") { DoubleValue(allowExperienceFromPvP) }
        it.addFunction("experience_share_multiplier") { DoubleValue(experienceShareMultiplier) }
        it.addFunction("lucky_egg_multiplier") { DoubleValue(luckyEggMultiplier) }
        it.addFunction("allow_spectating") { DoubleValue(allowSpectating) }
        it.addFunction("experience_multiplier") { DoubleValue(experienceMultiplier) }
        it.addFunction("pokemon_per_chunk") { DoubleValue(pokemonPerChunk) }
        it.addFunction("poke_snack_pokemon_per_chunk") { DoubleValue(pokeSnackPokemonPerChunk) }
        it.addFunction("infinite_healer_charge") { DoubleValue(infiniteHealerCharge) }
        it.addFunction("max_healer_charge") { DoubleValue(maxHealerCharge) }
        it.addFunction("seconds_to_charge_healing_machine") { DoubleValue(secondsToChargeHealingMachine) }
        it.addFunction("default_faint_timer") { DoubleValue(defaultFaintTimer) }
        it.addFunction("faint_awaken_health_percent") { DoubleValue(faintAwakenHealthPercent) }
        it.addFunction("heal_percent") { DoubleValue(healPercent) }
        it.addFunction("heal_timer") { DoubleValue(healTimer) }
        it.addFunction("base_apricorn_tree_generation_chance") { DoubleValue(baseApricornTreeGenerationChance) }
        it.addFunction("display_entity_level_label") { DoubleValue(displayEntityLevelLabel) }
        it.addFunction("display_entity_name_label") { DoubleValue(displayEntityNameLabel) }
        it.addFunction("display_name_for_unknown_pokemon") { DoubleValue(displayNameForUnknownPokemon) }
        it.addFunction("display_entity_labels_when_crouching_only") { DoubleValue(displayEntityLabelsWhenCrouchingOnly) }
        it.addFunction("shiny_rate") { DoubleValue(shinyRate) }
        it.addFunction("shiny_notice_particles_distance") { DoubleValue(shinyNoticeParticlesDistance) }
        it.addFunction("player_damage_pokemon") { DoubleValue(playerDamagePokemon) }
        it.addFunction("apple_leftovers_chance") { DoubleValue(appleLeftoversChance) }
        it.addFunction("max_roots_in_area") { DoubleValue(maxRootsInArea) }
        it.addFunction("big_root_propagation_chance") { DoubleValue(bigRootPropagationChance) }
        it.addFunction("energy_root_chance") { DoubleValue(energyRootChance) }
        it.addFunction("max_dynamax_level") { DoubleValue(maxDynamaxLevel) }
        it.addFunction("tera_type_rate") { DoubleValue(teraTypeRate) }
        it.addFunction("default_pastured_pokemon_limit") { DoubleValue(defaultPasturedPokemonLimit) }
        it.addFunction("pasture_block_update_ticks") { DoubleValue(pastureBlockUpdateTicks) }
        it.addFunction("pasture_max_wander_distance") { DoubleValue(pastureMaxWanderDistance) }
        it.addFunction("pasture_max_per_chunk") { DoubleValue(pastureMaxPerChunk) }
        it.addFunction("max_inserted_fossil_items") { DoubleValue(maxInsertedFossilItems) }
        it.addFunction("walking_in_battle_animations") { DoubleValue(walkingInBattleAnimations) }
        it.addFunction("battle_wild_max_distance") { DoubleValue(battleWildMaxDistance) }
        it.addFunction("trade_max_distance") { DoubleValue(tradeMaxDistance) }
        it.addFunction("battle_pvp_max_distance") { DoubleValue(battlePvPMaxDistance) }
        it.addFunction("battle_spectate_max_distance") { DoubleValue(battleSpectateMaxDistance) }
        it.addFunction("max_pokedex_scanning_detection_range") { DoubleValue(maxPokedexScanningDetectionRange) }
        it.addFunction("hide_unimplemented_pokemon_in_the_pokedex") { DoubleValue(hideUnimplementedPokemonInThePokedex) }
        it.addFunction("can_destiny_knot_break") { DoubleValue(canDestinyKnotBreak) }
        it.addFunction("requires_ots_for_masuda") { DoubleValue(requireOTsForMasuda) }
        it.addFunction("can_breed_genderless") { DoubleValue(canBreedGenderless) }
        it.addFunction("can_breed_undiscovered") { DoubleValue(canBreedUndiscovered) }
        it.addFunction("can_breed_ditto") { DoubleValue(canBreedDitto) }
        it.addFunction("minutes_per_egg_cycle") { DoubleValue(minutesPerEggCycle) }
        it.addFunction("nest_hatch_rate") { DoubleValue(nestHatchRate) }
        it.addFunction("incubator_hatch_rate") { DoubleValue(incubatorHatchRate) }
        it.addFunction("nether_nest_hatch_rate") { DoubleValue(netherNestHatchRate) }
        it.addFunction("nether_incubator_hatch_rate") { DoubleValue(netherIncubatorHatchRate) }
        it.addFunction("wild_breeding_chance") { DoubleValue(wildBreedingChance) }
        it.addFunction("pasture_breeding_chance") { DoubleValue(pastureBreedingChance) }
        it.addFunction("wild_breeding_cooldown_multiplier") { DoubleValue(wildBreedingCooldownMultiplier) }
        it.addFunction("pasture_breeding_cooldown_multiplier") { DoubleValue(pastureBreedingCooldownMultiplier) }
        it.addFunction("bench_extra_inherited_moves") { DoubleValue(benchExtraInheritedMoves) }
        it.addFunction("default_egg_texture_style") { StringValue(defaultEggTextureStyle.name.lowercase()) }
        it.addFunction("summary_pokemon_follow_cursor") { DoubleValue(summaryPokemonFollowCursor) }
        it.addFunction("party_portrait_animations") { StringValue(partyPortraitAnimations.name.lowercase()) }
        it.addFunction("pc_profile_animations") { StringValue(pcProfileAnimations.name.lowercase()) }
        it.addFunction("summary_profile_animations") { StringValue(summaryProfileAnimations.name.lowercase()) }
        it.addFunction("animate_battle_tiles") { DoubleValue(animateBattleTiles) }
        it.addFunction("third_person_view_bobbing") { DoubleValue(thirdPersonViewBobbing) }
        it.addFunction("invert_pitch") { DoubleValue(invertPitch) }
        it.addFunction("invert_yaw") { DoubleValue(invertYaw) }
        it.addFunction("x_axis_sensitivity") { DoubleValue(xAxisSensitivity) }
        it.addFunction("y_axis_sensitivity") { DoubleValue(yAxisSensitivity) }
        it.addFunction("swap_x_and_y_axes") { DoubleValue(swapXAndYAxes) }
        it.addFunction("disable_roll") { DoubleValue(disableRoll) }
        it.addFunction("display_controls_duration_seconds") { DoubleValue(displayControlSeconds) }
        it.addFunction("infinite_ride_stamina") { DoubleValue(infiniteRideStamina) }
        it.addFunction("remember_riding_camera") { DoubleValue(rememberRidingCamera) }
        it.addFunction("enable_in_flight_dismounting") { DoubleValue(enableInFlightDismounting) }
        it.addFunction("enable_debug_keys") { DoubleValue(enableDebugKeys) }
        it.addFunction("despawner_near_distance") { DoubleValue(despawnerNearDistance) }
        it.addFunction("despawner_far_distance") { DoubleValue(despawnerFarDistance) }
        it.addFunction("despawner_min_age_ticks") { DoubleValue(despawnerMinAgeTicks) }
        it.addFunction("despawner_max_age_ticks") { DoubleValue(despawnerMaxAgeTicks) }
        it.addFunction("monitor_alpha_rate") { DoubleValue(monitorAlphaRate) }
        it.addFunction("monitor_shiny_rate") { DoubleValue(monitorShinyRate) }
    }

    fun clone(): CobblemonConfig {
        val newConfig = CobblemonConfig()
        CobblemonConfig::class.memberProperties.forEach { property ->
            if (property is kotlin.reflect.KMutableProperty<*>) {
                property.isAccessible = true
                val value = property.getter.call(this)
                if (value != null) {
                    property.setter.call(newConfig, value)
                }
            }
        }
        return newConfig
    }
}
