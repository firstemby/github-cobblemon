/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.config

import com.cobblemon.mod.common.api.drop.ItemDropMethod
import com.cobblemon.mod.common.api.pokeball.catching.calculators.CaptureCalculator
import com.cobblemon.mod.common.api.pokemon.status.Statuses
import com.cobblemon.mod.common.client.gui.portrait.PortraitStyle
import com.cobblemon.mod.common.config.constraint.IntConstraint
import com.cobblemon.mod.common.pokeball.catching.calculators.CobblemonCaptureCalculator
import com.cobblemon.mod.common.util.adapters.CaptureCalculatorAdapter
import com.cobblemon.mod.common.util.adapters.IntRangeAdapter
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

class CobblemonConfig {
    companion object {
        val GSON = GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .registerTypeAdapter(IntRange::class.java, IntRangeAdapter)
            .registerTypeAdapter(ItemDropMethod::class.java, ItemDropMethod.adapter)
            .registerTypeAdapter(CaptureCalculator::class.java, CaptureCalculatorAdapter)
            .create()
    }

    var lastSavedVersion: String = "0.0.1"

    @CobblemonConfigField(Category.Pokemon, lang = "max_pokemon_level")
    @IntConstraint(min = 1, max = 1000)
    var maxPokemonLevel = 100

    @CobblemonConfigField(Category.Pokemon, lang = "max_pokemon_friendship")
    @IntConstraint(min = 0, max = 1000)
    var maxPokemonFriendship = 255

    @CobblemonConfigField(Category.Pokemon, lang = "announce_drop_items")
    var announceDropItems = true
    @CobblemonConfigField(Category.Pokemon, lang = "default_drop_item_method")
    var defaultDropItemMethod = ItemDropMethod.ON_ENTITY
    @CobblemonConfigField(Category.Pokemon, lang = "drops_after_death_animation")
    var dropAfterDeathAnimation = false

    @CobblemonConfigField(Category.Pokemon, lang = "ambient_pokemon_cry_ticks")
    @LastChangedVersion("1.4.0")
    var ambientPokemonCryTicks = 1080

    @CobblemonConfigField(Category.Storage, lang = "default_box_count")
    @IntConstraint(min = 1, max = 1000)
    var defaultBoxCount = 30
    @CobblemonConfigField(Category.Storage, lang = "pokemon_save_interval_seconds")
    @IntConstraint(min = 1, max = 120)
    var pokemonSaveIntervalSeconds = 30

    @CobblemonConfigField(Category.Storage, lang = "storage_format")
    var storageFormat = "nbt"

    @CobblemonConfigField(Category.Storage, lang = "prevent_complete_party_deposit")
    var preventCompletePartyDeposit = false

    @CobblemonConfigField(Category.Storage, lang = "mongo_db_connection_string")
    var mongoDBConnectionString = "mongodb://localhost:27017"
    @CobblemonConfigField(Category.Storage, lang = "mongo_db_database_name")
    var mongoDBDatabaseName = "cobblemon"

    @CobblemonConfigField(Category.Spawning, lang = "max_vertical_correction_blocks")
    @IntConstraint(min = 1, max = 200)
    var maxVerticalCorrectionBlocks = 64

    @CobblemonConfigField(Category.Spawning, lang = "minimum_level_range_max")
    @IntConstraint(min = 1, max = 1000)
    var minimumLevelRangeMax = 10

    @CobblemonConfigField(Category.Spawning, lang = "enable_spawning")
    var enableSpawning = true

    @CobblemonConfigField(Category.Spawning, lang = "minimum_distance_between_entities")
    var minimumDistanceBetweenEntities = 8.0

    @CobblemonConfigField(Category.Spawning, lang = "max_nearby_blocks_horizontal_range")
    var maxNearbyBlocksHorizontalRange = 4

    @CobblemonConfigField(Category.Spawning, lang = "max_nearby_blocks_vertical_range")
    var maxNearbyBlocksVerticalRange = 2

    @CobblemonConfigField(Category.Spawning, lang = "max_vertical_space")
    var maxVerticalSpace = 8

    @CobblemonConfigField(Category.Spawning, lang = "spawning_zone_diameter")
    @SerializedName("spawningZoneDiameter", alternate = ["worldSliceDiameter"])
    var spawningZoneDiameter = 8

    @CobblemonConfigField(Category.Spawning, lang = "spawning_zone_height")
    @SerializedName("spawningZoneHeight", alternate = ["worldSliceHeight"])
    var spawningZoneHeight = 16

    @CobblemonConfigField(Category.Spawning, lang = "ticks_between_spawn_attempts")
    var ticksBetweenSpawnAttempts = 20F

    @CobblemonConfigField(Category.Spawning, lang = "minimum_spawning_zone_distance_from_player")
    @SerializedName("minimumSpawningZoneDistanceFromPlayer", alternate = ["minimumSliceDistanceFromPlayer"])
    var minimumSpawningZoneDistanceFromPlayer = 16F

    @CobblemonConfigField(Category.Spawning, lang = "maximum_spawning_zone_distance_from_player")
    @SerializedName("maximumSpawningZoneDistanceFromPlayer", alternate = ["maximumSliceDistanceFromPlayer"])
    var maximumSpawningZoneDistanceFromPlayer = 16 * 4F

    @CobblemonConfigField(Category.Spawning, lang = "maximum_spawns_per_pass")
    var maximumSpawnsPerPass = 8

    @CobblemonConfigField(Category.Spawning, lang = "export_spawn_config")
    var exportSpawnConfig = false

    @CobblemonConfigField(Category.Spawning, lang = "save_pokemon_to_world")
    var savePokemonToWorld = true

    @CobblemonConfigField(Category.Starter, lang = "export_starter_config")
    var exportStarterConfig = false

    @CobblemonConfigField(Category.Battles, lang = "auto_update_showdown")
    var autoUpdateShowdown = true

    @CobblemonConfigField(Category.Battles, lang = "default_flee_distance")
    var defaultFleeDistance = 16F * 2

    @CobblemonConfigField(Category.Battles, lang = "allow_experience_from_pvp")
    var allowExperienceFromPvP = true

    @CobblemonConfigField(Category.Battles, lang = "experience_share_multiplier")
    var experienceShareMultiplier = .5

    @CobblemonConfigField(Category.Battles, lang = "lucky_egg_multiplier")
    var luckyEggMultiplier = 1.5

    @CobblemonConfigField(Category.Battles, lang = "allow_spectating")
    var allowSpectating = true

    @CobblemonConfigField(Category.Pokemon, lang = "experience_multiplier")
    var experienceMultiplier = 2F

    @CobblemonConfigField(Category.Spawning, lang = "pokemon_per_chunk")
    var pokemonPerChunk = 1F

    @CobblemonConfigField(Category.PassiveStatus, lang = "passive_statuses")
    var passiveStatuses = mutableMapOf(
        Statuses.POISON.configEntry(),
        Statuses.POISON_BADLY.configEntry(),
        Statuses.PARALYSIS.configEntry(),
        Statuses.FROZEN.configEntry(),
        Statuses.SLEEP.configEntry(),
        Statuses.BURN.configEntry()
    )

    @CobblemonConfigField(Category.Healing, lang = "infinite_healer_charge")
    var infiniteHealerCharge = false

    @CobblemonConfigField(Category.Healing, lang = "max_healer_charge")
    var maxHealerCharge = 6.0f

    @CobblemonConfigField(Category.Healing, lang = "seconds_to_charge_healing_machine")
    var secondsToChargeHealingMachine = 900.0

    @CobblemonConfigField(Category.Healing, lang = "default_faint_timer")
    var defaultFaintTimer = 300

    @CobblemonConfigField(Category.Healing, lang = "faint_awaken_health_percent")
    var faintAwakenHealthPercent = 0.2f

    @CobblemonConfigField(Category.Healing, lang = "heal_percent")
    var healPercent = 0.05

    @CobblemonConfigField(Category.Healing, lang = "heal_timer")
    var healTimer = 60

    @CobblemonConfigField(Category.Spawning, lang = "base_apricorn_tree_generation_chance")
    var baseApricornTreeGenerationChance = 0.1F

    @CobblemonConfigField(Category.Pokemon, lang = "display_entity_level_label")
    var displayEntityLevelLabel = true

    @CobblemonConfigField(Category.Pokemon, lang = "display_entity_name_label")
    var displayEntityNameLabel = true

    @CobblemonConfigField(Category.Pokemon, lang = "display_name_for_unknown_pokemon")
    var displayNameForUnknownPokemon = false

    @CobblemonConfigField(Category.Pokemon, lang = "display_entity_labels_when_crouching_only")
    var displayEntityLabelsWhenCrouchingOnly = false

    @CobblemonConfigField(Category.Spawning, lang = "shiny_rate")
    var shinyRate = 8192F

    @CobblemonConfigField(Category.Pokemon, lang = "shiny_notice_particles_distance")
    var shinyNoticeParticlesDistance = 24F

    @CobblemonConfigField(Category.Pokemon, lang = "capture_calculator")
    var captureCalculator: CaptureCalculator = CobblemonCaptureCalculator

    @CobblemonConfigField(Category.Pokemon, lang = "player_damage_pokemon")
    var playerDamagePokemon = true

    @CobblemonConfigField(Category.World, lang = "apple_leftovers_chance")
    var appleLeftoversChance = 0.025

    @CobblemonConfigField(Category.World, lang = "max_roots_in_area")
    var maxRootsInArea = 5

    @CobblemonConfigField(Category.World, lang = "big_root_propagation_chance")
    var bigRootPropagationChance = 0.1

    @CobblemonConfigField(Category.World, lang = "energy_root_chance")
    var energyRootChance = 0.25

    @CobblemonConfigField(Category.Pokemon, lang = "max_dynamax_level")
    @IntConstraint(min = 0, max = 10)
    var maxDynamaxLevel = 10

    @CobblemonConfigField(Category.Spawning, lang = "tera_type_rate")
    var teraTypeRate = 20F

    @CobblemonConfigField(Category.World, lang = "default_pastured_pokemon_limit")
    var defaultPasturedPokemonLimit = 16

    @CobblemonConfigField(Category.World, lang = "pasture_block_update_ticks")
    var pastureBlockUpdateTicks = 40

    @CobblemonConfigField(Category.World, lang = "pasture_max_wander_distance")
    var pastureMaxWanderDistance = 64

    @CobblemonConfigField(Category.World, lang = "pasture_max_per_chunk")
    var pastureMaxPerChunk = 4F

    @CobblemonConfigField(Category.World, lang = "max_inserted_fossil_items")
    var maxInsertedFossilItems = 2

    @CobblemonConfigField(Category.Battles, lang = "walking_in_battle_animations")
    var walkingInBattleAnimations = false

    @CobblemonConfigField(Category.Battles, lang = "battle_wild_max_distance")
    var battleWildMaxDistance = 12F

    @CobblemonConfigField(Category.World, lang = "trade_max_distance")
    var tradeMaxDistance = 12F

    @CobblemonConfigField(Category.Battles, lang = "battle_pvp_max_distance")
    @SerializedName("battlePvPMaxDistance", alternate = ["BattlePvPMaxDistance"])
    var battlePvPMaxDistance = 32F

    @CobblemonConfigField(Category.Battles, lang = "battle_spectate_max_distance")
    var battleSpectateMaxDistance = 64F

    @CobblemonConfigField(Category.Pokedex, lang = "max_pokedex_scanning_detection_range")
    var maxPokedexScanningDetectionRange = 10.0

    @CobblemonConfigField(Category.Pokedex, lang = "hide_unimplemented_pokemon_in_the_pokedex")
    var hideUnimplementedPokemonInThePokedex = false

    @CobblemonConfigField(Category.Interface, lang = "summary_pokemon_follow_cursor")
    var summaryPokemonFollowCursor = true

    @CobblemonConfigField(Category.Interface, lang = "party_portrait_animations")
    var partyPortraitAnimations = PortraitStyle.NEVER_ANIMATE

    @CobblemonConfigField(Category.Riding, lang = "third_person_view_bobbing")
    var thirdPersonViewBobbing = true

    @CobblemonConfigField(Category.Riding, lang = "invert_roll")
    var invertRoll = false

    @CobblemonConfigField(Category.Riding, lang = "invert_pitch")
    var invertPitch = false

    @CobblemonConfigField(Category.Riding, lang = "invert_yaw")
    var invertYaw = false

    @CobblemonConfigField(Category.Riding, lang = "automatic_righting_delay")
    var rightingDelay = -1.0

    @CobblemonConfigField(Category.Riding, lang = "disable_roll")
    var disableRoll = false

    @CobblemonConfigField(Category.Riding, lang = "display_controls_duration_seconds")
    var displayControlSeconds = 3

    @CobblemonConfigField(Category.Debug, lang = "enable_debug_keys")
    var enableDebugKeys = true

    @CobblemonConfigField(Category.Spawning, lang = "despawner_near_distance")
    var despawnerNearDistance = 32f
    
    @CobblemonConfigField(Category.Spawning, lang = "despawner_far_distance")
    var despawnerFarDistance = 96f
    
    @CobblemonConfigField(Category.Spawning, lang = "despawner_min_age_ticks")
    var despawnerMinAgeTicks = 600
    
    @CobblemonConfigField(Category.Spawning, lang = "despawner_max_age_ticks")
    var despawnerMaxAgeTicks = 3600

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
