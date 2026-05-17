/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokemon.battletheme

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.conditional.RegistryLikeCondition
import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.api.spawning.MoonPhaseRange
import com.cobblemon.mod.common.api.spawning.TimeRange
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.adapters.*
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.mojang.datafixers.util.Either
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.tags.TagKey
import net.minecraft.world.level.Level
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.levelgen.structure.Structure

/**
 * Context passed to [PokemonBattleThemeOverrides.resolve] describing what entity is initiating a battle
 * and where it is in the world. All fields are optional; omitted fields skip their respective condition checks.
 */
data class BattleThemeContext(
    val pokemon: Pokemon? = null,
    val npc: NPCEntity? = null,
    val player: ServerPlayer? = null,
    val level: Level? = null,
    val pos: BlockPos? = null
)

/**
 * A single battle-theme override entry loaded from a datapack JSON file.
 * All [properties] must match for the override to apply. Higher [weight] is checked first.
 * World conditions ([biomes], [dimensions], [timeRange], etc.) are only evaluated when a
 * world position is available.
 */
/** The type of battle an override applies to. Omitting [battleTypes] on an entry matches all types. */
enum class BattleThemeType { PVP, PVW, PVN }

data class PokemonBattleThemeOverrideEntry(
    @SerializedName("theme")        val theme: ResourceLocation,
    @SerializedName("properties")   val properties: List<String> = emptyList(),
    @SerializedName("battleTypes")  val battleTypes: Set<BattleThemeType>? = null,
    @SerializedName("weight")       val weight: Int = 0,
    @SerializedName("biomes")       val biomes: MutableSet<RegistryLikeCondition<Biome>>? = null,
    @SerializedName("dimensions")   val dimensions: List<ResourceLocation>? = null,
    @SerializedName("timeRange")    val timeRange: TimeRange? = null,
    @SerializedName("moonPhase")    val moonPhase: MoonPhaseRange? = null,
    @SerializedName("isRaining")    val isRaining: Boolean? = null,
    @SerializedName("isThundering") val isThundering: Boolean? = null,
    @SerializedName("canSeeSky")    val canSeeSky: Boolean? = null,
    @SerializedName("minLight")     val minLight: Int? = null,
    @SerializedName("maxLight")     val maxLight: Int? = null,
    @SerializedName("minSkyLight")  val minSkyLight: Int? = null,
    @SerializedName("maxSkyLight")  val maxSkyLight: Int? = null,
    @SerializedName("structures")   val structures: List<Either<ResourceLocation, TagKey<Structure>>>? = null
)

private data class ProgrammaticOverride(
    val theme: ResourceLocation,
    val predicate: (BattleThemeContext) -> Boolean
)

/**
 * The data registry for battle theme overrides.
 * Entries are loaded from `data/<namespace>/battle_theme_overrides/` and evaluated by descending weight.
 * Programmatic overrides registered via [register] are checked after datapack entries.
 */
object PokemonBattleThemeOverrides : JsonDataRegistry<PokemonBattleThemeOverrideEntry> {

    override val id           = cobblemonResource("battle_theme_overrides")
    override val type         = PackType.SERVER_DATA
    override val observable   = SimpleObservable<PokemonBattleThemeOverrides>()
    override val resourcePath = "battle_theme_overrides"
    override val typeToken    = TypeToken.get(PokemonBattleThemeOverrideEntry::class.java)

    override val gson = GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
        .registerTypeAdapter(TypeToken.getParameterized(RegistryLikeCondition::class.java, Biome::class.java).type, BiomeLikeConditionAdapter)
        .registerTypeAdapter(TimeRange::class.java, IntRangesAdapter(TimeRange.timeRanges) { TimeRange(*it) })
        .registerTypeAdapter(MoonPhaseRange::class.java, IntRangesAdapter(MoonPhaseRange.moonPhaseRanges) { MoonPhaseRange(*it) })
        .registerTypeAdapter(
            TypeToken.getParameterized(
                Either::class.java,
                ResourceLocation::class.java,
                TypeToken.getParameterized(TagKey::class.java, Structure::class.java).type
            ).type,
            EitherIdentifierOrTagAdapter(Registries.STRUCTURE)
        )
        .create()

    private val datapackEntries  = mutableListOf<PokemonBattleThemeOverrideEntry>()
    private val programmaticList = mutableListOf<ProgrammaticOverride>()

    override fun reload(data: Map<ResourceLocation, PokemonBattleThemeOverrideEntry>) {
        datapackEntries.clear()
        datapackEntries += data.values.sortedByDescending { it.weight }
        Cobblemon.LOGGER.info("Loaded {} battle_theme_override(s)", datapackEntries.size)
        observable.emit(this)
    }

    override fun sync(player: ServerPlayer) = Unit

    fun register(theme: ResourceLocation, predicate: (BattleThemeContext) -> Boolean) {
        programmaticList += ProgrammaticOverride(theme, predicate)
    }

    fun unregister(theme: ResourceLocation) {
        programmaticList.removeIf { it.theme == theme }
    }

    /**
     * Returns the first matching override theme for the given [context], or null if none match.
     * When a [BattleThemeContext.pokemon] is present, entry properties are matched via [PokemonProperties].
     * When only a [BattleThemeContext.npc] is present, properties are matched against the NPC entity's aspects.
     * World conditions are only evaluated when [BattleThemeContext.level] and [BattleThemeContext.pos] are present.
     */
    fun resolve(context: BattleThemeContext): ResourceLocation? {
        val battleType = when {
            context.pokemon != null -> BattleThemeType.PVW
            context.npc     != null -> BattleThemeType.PVN
            context.player  != null -> BattleThemeType.PVP
            else -> null
        }
        for (entry in datapackEntries) {
            if (entry.battleTypes != null && battleType !in entry.battleTypes) continue
            val propertiesMatch = entry.properties.isEmpty() || when {
                context.pokemon != null -> entry.properties.all { PokemonProperties.parse(it).matches(context.pokemon) }
                context.npc != null    -> entry.properties.all { it in context.npc.aspects }
                else -> false
            }
            if (propertiesMatch && fitsWorldConditions(entry, context.level, context.pos)) return entry.theme
        }
        for (override in programmaticList) {
            if (override.predicate(context)) return override.theme
        }
        return null
    }

    private fun fitsWorldConditions(entry: PokemonBattleThemeOverrideEntry, level: Level?, pos: BlockPos?): Boolean {
        if (level == null || pos == null) return true
        if (entry.dimensions != null && level.dimension().location() !in entry.dimensions) return false
        if (entry.biomes != null && entry.biomes.isNotEmpty() && entry.biomes.none { it.fits(level.getBiome(pos)) }) return false
        if (entry.timeRange != null && !entry.timeRange.contains((level.dayTime() % 24000).toInt())) return false
        if (entry.moonPhase != null && level.moonPhase !in entry.moonPhase) return false
        if (entry.isRaining != null && level.isRaining != entry.isRaining) return false
        if (entry.isThundering != null && level.isThundering != entry.isThundering) return false
        if (entry.canSeeSky != null && level.canSeeSky(pos) != entry.canSeeSky) return false
        val blockLight = level.getBrightness(LightLayer.BLOCK, pos)
        if (entry.minLight != null && blockLight < entry.minLight) return false
        if (entry.maxLight != null && blockLight > entry.maxLight) return false
        val skyLight = level.getBrightness(LightLayer.SKY, pos)
        if (entry.minSkyLight != null && skyLight < entry.minSkyLight) return false
        if (entry.maxSkyLight != null && skyLight > entry.maxSkyLight) return false
        if (entry.structures != null && entry.structures.isNotEmpty() && level is ServerLevel) {
            val structureManager = level.structureManager()
            val structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
            val inAny = entry.structures.any { either ->
                either.map(
                    { id -> structureRegistry.get(id)?.let { structureManager.getStructureWithPieceAt(pos, it).isValid } ?: false },
                    { tag -> structureRegistry.getTagOrEmpty(tag).any { structureManager.getStructureWithPieceAt(pos, it.value()).isValid } }
                )
            }
            if (!inAny) return false
        }
        return true
    }
}
