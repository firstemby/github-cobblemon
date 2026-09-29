/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.stadium

import com.cobblemon.mod.common.util.lang
import net.minecraft.network.chat.Component
import com.cobblemon.mod.common.util.DataKeys
import java.util.UUID
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3

data class StadiumBlockPlacement(
    var position: Vec3,
    var lookAt: Vec3? = null,
    var yaw: Float? = null,
    var pitch: Float? = null
) {
    fun toNbt(): CompoundTag {
        val tag = CompoundTag()
        putVec3(tag, DataKeys.STADIUM_PLACEMENT_POSITION, position)
        lookAt?.let { putVec3(tag, DataKeys.STADIUM_PLACEMENT_LOOK_AT, it) }
        yaw?.let { tag.putFloat(DataKeys.STADIUM_PLACEMENT_YAW, it) }
        pitch?.let { tag.putFloat(DataKeys.STADIUM_PLACEMENT_PITCH, it) }
        return tag
    }

    companion object {
        fun fromNbt(tag: CompoundTag): StadiumBlockPlacement {
            return StadiumBlockPlacement(
                position = getVec3(tag, DataKeys.STADIUM_PLACEMENT_POSITION) ?: Vec3.ZERO,
                lookAt = getVec3(tag, DataKeys.STADIUM_PLACEMENT_LOOK_AT),
                yaw = if (tag.contains(DataKeys.STADIUM_PLACEMENT_YAW)) tag.getFloat(DataKeys.STADIUM_PLACEMENT_YAW) else null,
                pitch = if (tag.contains(DataKeys.STADIUM_PLACEMENT_PITCH)) tag.getFloat(DataKeys.STADIUM_PLACEMENT_PITCH) else null
            )
        }
    }
}

data class StadiumBlockSide(
    val actors: MutableList<StadiumBlockPlacement>,
    val pokemon: MutableList<StadiumBlockPlacement>
) {
    fun deepCopy(): StadiumBlockSide = StadiumBlockSide(
        actors = actors.map { it.copy() }.toMutableList(),
        pokemon = pokemon.map { it.copy() }.toMutableList()
    )

    fun toNbt(): CompoundTag {
        val tag = CompoundTag()
        tag.put(DataKeys.STADIUM_SIDE_ACTORS, toPlacementList(actors))
        tag.put(DataKeys.STADIUM_SIDE_POKEMON, toPlacementList(pokemon))
        return tag
    }

    companion object {
        fun fromNbt(tag: CompoundTag): StadiumBlockSide {
            return StadiumBlockSide(
                actors = fromPlacementList(tag, DataKeys.STADIUM_SIDE_ACTORS),
                pokemon = fromPlacementList(tag, DataKeys.STADIUM_SIDE_POKEMON)
            )
        }
    }
}

data class StadiumBlockLayout(
    val side1: StadiumBlockSide,
    val side2: StadiumBlockSide,
    var trainerSlots: Int = 1,
    var pokemonSlots: Int = 1
) {
    fun deepCopy(): StadiumBlockLayout = StadiumBlockLayout(
        side1 = side1.deepCopy(),
        side2 = side2.deepCopy(), trainerSlots = trainerSlots, pokemonSlots = pokemonSlots
    )

    fun toNbt(): CompoundTag {
        val tag = CompoundTag()
        tag.put(DataKeys.STADIUM_LAYOUT_SIDE1, side1.toNbt())
        tag.put(DataKeys.STADIUM_LAYOUT_SIDE2, side2.toNbt())
        tag.putInt("TrainerSlots", trainerSlots)
        tag.putInt("PokemonSlots", pokemonSlots)
        return tag
    }

    companion object {
        fun createDefault(): StadiumBlockLayout = StadiumBlockLayout(
            side1 = StadiumBlockSide(actors = mutableListOf(), pokemon = mutableListOf()),
            side2 = StadiumBlockSide(actors = mutableListOf(), pokemon = mutableListOf())
        )

        fun fromNbt(tag: CompoundTag): StadiumBlockLayout {
            return StadiumBlockLayout(
                side1 = if (tag.contains(DataKeys.STADIUM_LAYOUT_SIDE1, Tag.TAG_COMPOUND.toInt()))
                    StadiumBlockSide.fromNbt(tag.getCompound(DataKeys.STADIUM_LAYOUT_SIDE1))
                else StadiumBlockSide(mutableListOf(), mutableListOf()),
                side2 = if (tag.contains(DataKeys.STADIUM_LAYOUT_SIDE2, Tag.TAG_COMPOUND.toInt()))
                    StadiumBlockSide.fromNbt(tag.getCompound(DataKeys.STADIUM_LAYOUT_SIDE2))
                else StadiumBlockSide(mutableListOf(), mutableListOf()),
                trainerSlots = if (tag.contains("TrainerSlots")) tag.getInt("TrainerSlots") else 1,
                pokemonSlots = if (tag.contains("PokemonSlots")) tag.getInt("PokemonSlots") else 1
            )
        }
    }
}

data class StadiumBlockData(
    val stadiumId: UUID,
    var name: String,
    var ownerUUID: UUID?,
    val formatConfigs: MutableMap<String, StadiumFormatConfig>,
    var activeFormat: String,
    var singlesLayout: StadiumBlockLayout?,
    var doublesLayout: StadiumBlockLayout?,
    var cameraOriginOffset: Vec3,
    var isConfigured: Boolean,
    var cinematicScene: ResourceLocation? = null,
    var cinematicSceneJson: String? = null,
    var arena: StadiumArena? = null,
    val additionalLayouts: MutableMap<String, StadiumBlockLayout> = mutableMapOf(),
    var cinematicLayout: String = "singles",
    val spectatorAreas: MutableList<StadiumSpectatorArea> = mutableListOf()
) {
    val displayName: Component get() = if (name.isEmpty()) lang("stadium.default_name") else Component.literal(name)

    fun layout(id: String): StadiumBlockLayout? = when (id) {
        "singles" -> singlesLayout
        "doubles" -> doublesLayout
        else -> additionalLayouts[id]
    }

    fun setLayout(id: String, layout: StadiumBlockLayout) {
        when (id) {
            "singles" -> singlesLayout = layout
            "doubles" -> doublesLayout = layout
            else -> additionalLayouts[id] = layout
        }
    }

    fun slots(id: String, type: String): Int = when (id) {
        "singles" -> 1
        "doubles" -> if (type == "actors") 1 else 2
        else -> layout(id)?.let { if (type == "actors") it.trainerSlots else it.pokemonSlots } ?: 0
    }

    fun toNbt(): CompoundTag {
        val tag = CompoundTag()
        tag.putUUID(DataKeys.STADIUM_BLOCK_ID, stadiumId)
        tag.putString(DataKeys.STADIUM_BLOCK_NAME, name)
        ownerUUID?.let { tag.putUUID(DataKeys.STADIUM_BLOCK_OWNER, it) }
        tag.putString(DataKeys.STADIUM_BLOCK_ACTIVE_FORMAT, activeFormat)
        tag.putBoolean(DataKeys.STADIUM_BLOCK_CONFIGURED, isConfigured)
        cinematicScene?.let { tag.putString("CinematicScene", it.toString()) }
        cinematicSceneJson?.let { tag.putByteArray("CinematicSceneJson", it.toByteArray(Charsets.UTF_8)) }

        val formatsTag = CompoundTag()
        for ((key, config) in formatConfigs) {
            formatsTag.put(key, config.toNbt())
        }
        tag.put(DataKeys.STADIUM_BLOCK_FORMAT_CONFIGS, formatsTag)

        singlesLayout?.let { tag.put(DataKeys.STADIUM_BLOCK_SINGLES_LAYOUT, it.toNbt()) }
        doublesLayout?.let { tag.put(DataKeys.STADIUM_BLOCK_DOUBLES_LAYOUT, it.toNbt()) }
        arena?.let { tag.put("Arena", it.toNbt()) }
        tag.put("SpectatorAreas", ListTag().apply { spectatorAreas.forEach { add(it.toNbt()) } })
        tag.put("AdditionalLayouts", CompoundTag().apply { additionalLayouts.forEach { (id, layout) -> put(id, layout.toNbt()) } })
        tag.putString("CinematicLayout", cinematicLayout)

        putVec3(tag, DataKeys.STADIUM_BLOCK_CAMERA_OFFSET, cameraOriginOffset)

        return tag
    }

    companion object {
        fun createDefault(stadiumId: UUID = UUID.randomUUID()): StadiumBlockData {
            return StadiumBlockData(
                stadiumId = stadiumId,
                name = "",
                ownerUUID = null,
                formatConfigs = mutableMapOf("singles" to StadiumConfiguration.template(), "doubles" to StadiumConfiguration.template(true)),
                activeFormat = "singles",
                singlesLayout = null,
                doublesLayout = null,
                cameraOriginOffset = Vec3.ZERO,
                isConfigured = false
            )
        }

        fun fromNbt(tag: CompoundTag): StadiumBlockData {
            val stadiumId = if (tag.hasUUID(DataKeys.STADIUM_BLOCK_ID)) tag.getUUID(DataKeys.STADIUM_BLOCK_ID) else UUID.randomUUID()
            val name = tag.getString(DataKeys.STADIUM_BLOCK_NAME)
            val ownerUUID = if (tag.hasUUID(DataKeys.STADIUM_BLOCK_OWNER)) tag.getUUID(DataKeys.STADIUM_BLOCK_OWNER) else null
            val activeFormat = tag.getString(DataKeys.STADIUM_BLOCK_ACTIVE_FORMAT)
            val sceneName = tag.getString("CinematicScene").takeIf { it.isNotBlank() }
            val cinematicScene = sceneName?.let(ResourceLocation::tryParse)
            val isConfigured = tag.getBoolean(DataKeys.STADIUM_BLOCK_CONFIGURED) && (sceneName == null || cinematicScene != null)

            val formatConfigs = mutableMapOf<String, StadiumFormatConfig>()
            if (tag.contains(DataKeys.STADIUM_BLOCK_FORMAT_CONFIGS, Tag.TAG_COMPOUND.toInt())) {
                val formatsTag = tag.getCompound(DataKeys.STADIUM_BLOCK_FORMAT_CONFIGS)
                for (key in formatsTag.allKeys) {
                    formatConfigs[key] = StadiumFormatConfig.fromNbt(formatsTag.getCompound(key))
                }
            }

            val singlesLayout = if (tag.contains(DataKeys.STADIUM_BLOCK_SINGLES_LAYOUT, Tag.TAG_COMPOUND.toInt()))
                StadiumBlockLayout.fromNbt(tag.getCompound(DataKeys.STADIUM_BLOCK_SINGLES_LAYOUT))
            else null

            val doublesLayout = if (tag.contains(DataKeys.STADIUM_BLOCK_DOUBLES_LAYOUT, Tag.TAG_COMPOUND.toInt()))
                StadiumBlockLayout.fromNbt(tag.getCompound(DataKeys.STADIUM_BLOCK_DOUBLES_LAYOUT))
            else null

            val cameraOriginOffset = getVec3(tag, DataKeys.STADIUM_BLOCK_CAMERA_OFFSET) ?: Vec3.ZERO

            return StadiumBlockData(
                stadiumId = stadiumId,
                name = name,
                ownerUUID = ownerUUID,
                formatConfigs = formatConfigs,
                activeFormat = activeFormat,
                singlesLayout = singlesLayout,
                doublesLayout = doublesLayout,
                cameraOriginOffset = cameraOriginOffset,
                isConfigured = isConfigured,
                cinematicScene = cinematicScene,
                cinematicSceneJson = tag.getByteArray("CinematicSceneJson").toString(Charsets.UTF_8).takeIf { it.isNotBlank() },
                arena = if (tag.contains("Arena", Tag.TAG_COMPOUND.toInt())) StadiumArena.fromNbt(tag.getCompound("Arena")) else null,
                additionalLayouts = tag.getCompound("AdditionalLayouts").let { layouts ->
                    layouts.allKeys.associateWith { StadiumBlockLayout.fromNbt(layouts.getCompound(it)) }.toMutableMap()
                },
                cinematicLayout = tag.getString("CinematicLayout").ifEmpty { "singles" },
                spectatorAreas = tag.getList("SpectatorAreas", Tag.TAG_COMPOUND.toInt()).map {
                    StadiumSpectatorArea.fromNbt(it as CompoundTag)
                }.toMutableList()
            )
        }
    }
}

data class StadiumFormatConfig(
    val formatName: String,
    val battleTypeName: String,
    val clauses: MutableSet<String>,
    val levelCap: Int,
    val teamPreview: Boolean,
    val pokemonLimit: Int,
    val moveTimer: Int,
    val matchTimerMinutes: Int,
    val bannedPokemon: MutableSet<String>,
    val bannedMoves: MutableSet<String>,
    val bannedAbilities: MutableSet<String>,
    val bannedItems: MutableSet<String>,
    val allowedGimmicks: MutableSet<String>,
    val allowHeldItems: Boolean = true,
    val uniqueHeldItems: Boolean = false
) {
    val displayName: Component get() = if (formatName.isEmpty()) lang("stadium.layout.$battleTypeName") else Component.literal(formatName)

    fun toNbt(): CompoundTag {
        val tag = CompoundTag()
        tag.putString(DataKeys.STADIUM_FORMAT_NAME, formatName)
        tag.putString(DataKeys.STADIUM_FORMAT_BATTLE_TYPE, battleTypeName)
        tag.put(DataKeys.STADIUM_FORMAT_CLAUSES, toStringList(clauses))
        tag.putInt(DataKeys.STADIUM_FORMAT_LEVEL_CAP, levelCap)
        tag.putBoolean(DataKeys.STADIUM_FORMAT_TEAM_PREVIEW, teamPreview)
        tag.putInt(DataKeys.STADIUM_FORMAT_POKEMON_LIMIT, pokemonLimit)
        tag.putInt(DataKeys.STADIUM_FORMAT_MOVE_TIMER, moveTimer)
        tag.putInt(DataKeys.STADIUM_FORMAT_MATCH_TIMER, matchTimerMinutes)
        tag.put(DataKeys.STADIUM_FORMAT_BANNED_POKEMON, toStringList(bannedPokemon))
        tag.put(DataKeys.STADIUM_FORMAT_BANNED_MOVES, toStringList(bannedMoves))
        tag.put(DataKeys.STADIUM_FORMAT_BANNED_ABILITIES, toStringList(bannedAbilities))
        tag.put(DataKeys.STADIUM_FORMAT_BANNED_ITEMS, toStringList(bannedItems))
        tag.put(DataKeys.STADIUM_FORMAT_ALLOWED_GIMMICKS, toStringList(allowedGimmicks))
        tag.putBoolean(DataKeys.STADIUM_FORMAT_ALLOW_HELD_ITEMS, allowHeldItems)
        tag.putBoolean(DataKeys.STADIUM_FORMAT_UNIQUE_HELD_ITEMS, uniqueHeldItems)
        return tag
    }

    companion object {
        fun fromNbt(tag: CompoundTag): StadiumFormatConfig {
            return StadiumFormatConfig(
                formatName = tag.getString(DataKeys.STADIUM_FORMAT_NAME),
                battleTypeName = tag.getString(DataKeys.STADIUM_FORMAT_BATTLE_TYPE).ifEmpty { "singles" },
                clauses = fromStringList(tag, DataKeys.STADIUM_FORMAT_CLAUSES),
                levelCap = if (tag.contains(DataKeys.STADIUM_FORMAT_LEVEL_CAP)) tag.getInt(DataKeys.STADIUM_FORMAT_LEVEL_CAP) else -1,
                teamPreview = tag.getBoolean(DataKeys.STADIUM_FORMAT_TEAM_PREVIEW),
                pokemonLimit = if (tag.contains(DataKeys.STADIUM_FORMAT_POKEMON_LIMIT)) tag.getInt(DataKeys.STADIUM_FORMAT_POKEMON_LIMIT) else 6,
                moveTimer = if (tag.contains(DataKeys.STADIUM_FORMAT_MOVE_TIMER)) tag.getInt(DataKeys.STADIUM_FORMAT_MOVE_TIMER) else -1,
                matchTimerMinutes = if (tag.contains(DataKeys.STADIUM_FORMAT_MATCH_TIMER)) tag.getInt(DataKeys.STADIUM_FORMAT_MATCH_TIMER) else -1,
                bannedPokemon = fromStringList(tag, DataKeys.STADIUM_FORMAT_BANNED_POKEMON),
                bannedMoves = fromStringList(tag, DataKeys.STADIUM_FORMAT_BANNED_MOVES),
                bannedAbilities = fromStringList(tag, DataKeys.STADIUM_FORMAT_BANNED_ABILITIES),
                bannedItems = fromStringList(tag, DataKeys.STADIUM_FORMAT_BANNED_ITEMS),
                allowedGimmicks = fromStringList(tag, DataKeys.STADIUM_FORMAT_ALLOWED_GIMMICKS),
                allowHeldItems = !tag.contains(DataKeys.STADIUM_FORMAT_ALLOW_HELD_ITEMS) || tag.getBoolean(DataKeys.STADIUM_FORMAT_ALLOW_HELD_ITEMS),
                uniqueHeldItems = tag.getBoolean(DataKeys.STADIUM_FORMAT_UNIQUE_HELD_ITEMS)
            )
        }

        private fun toStringList(set: Collection<String>): ListTag {
            val list = ListTag()
            for (s in set) {
                list.add(StringTag.valueOf(s))
            }
            return list
        }

        private fun fromStringList(tag: CompoundTag, key: String): MutableSet<String> {
            val set = mutableSetOf<String>()
            if (tag.contains(key, Tag.TAG_LIST.toInt())) {
                val list = tag.getList(key, Tag.TAG_STRING.toInt())
                for (i in 0 until list.size) {
                    set.add(list.getString(i))
                }
            }
            return set
        }
    }
}

private fun putVec3(tag: CompoundTag, key: String, vec: Vec3) {
    val vecTag = CompoundTag()
    vecTag.putDouble("X", vec.x)
    vecTag.putDouble("Y", vec.y)
    vecTag.putDouble("Z", vec.z)
    tag.put(key, vecTag)
}

private fun getVec3(tag: CompoundTag, key: String): Vec3? {
    if (!tag.contains(key, Tag.TAG_COMPOUND.toInt())) return null
    val vecTag = tag.getCompound(key)
    return Vec3(vecTag.getDouble("X"), vecTag.getDouble("Y"), vecTag.getDouble("Z"))
}

private fun toPlacementList(placements: List<StadiumBlockPlacement>): ListTag {
    val list = ListTag()
    for (p in placements) {
        list.add(p.toNbt())
    }
    return list
}

private fun fromPlacementList(tag: CompoundTag, key: String): MutableList<StadiumBlockPlacement> {
    val result = mutableListOf<StadiumBlockPlacement>()
    if (tag.contains(key, Tag.TAG_LIST.toInt())) {
        val list = tag.getList(key, Tag.TAG_COMPOUND.toInt())
        for (i in 0 until list.size) {
            result.add(StadiumBlockPlacement.fromNbt(list.getCompound(i)))
        }
    }
    return result
}
