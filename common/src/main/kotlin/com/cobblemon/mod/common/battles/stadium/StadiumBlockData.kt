/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.stadium

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
    val side2: StadiumBlockSide
) {
    fun deepCopy(): StadiumBlockLayout = StadiumBlockLayout(
        side1 = side1.deepCopy(),
        side2 = side2.deepCopy()
    )

    fun toNbt(): CompoundTag {
        val tag = CompoundTag()
        tag.put(DataKeys.STADIUM_LAYOUT_SIDE1, side1.toNbt())
        tag.put(DataKeys.STADIUM_LAYOUT_SIDE2, side2.toNbt())
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
                else StadiumBlockSide(mutableListOf(), mutableListOf())
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
    var selectedDefaultFormats: MutableSet<String>,
    var singlesLayout: StadiumBlockLayout?,
    var doublesLayout: StadiumBlockLayout?,
    var cameraTimeline: ResourceLocation?,
    var cameraTimelineSideB: ResourceLocation?,
    var cameraOriginOffset: Vec3,
    var introMusic: ResourceLocation?,
    var introMusicVolume: Float,
    var introMusicPitch: Float,
    var loopMusic: ResourceLocation?,
    var loopMusicVolume: Float,
    var loopMusicPitch: Float,
    var isConfigured: Boolean
) {
    fun toNbt(): CompoundTag {
        val tag = CompoundTag()
        tag.putUUID(DataKeys.STADIUM_BLOCK_ID, stadiumId)
        tag.putString(DataKeys.STADIUM_BLOCK_NAME, name)
        ownerUUID?.let { tag.putUUID(DataKeys.STADIUM_BLOCK_OWNER, it) }
        tag.putString(DataKeys.STADIUM_BLOCK_ACTIVE_FORMAT, activeFormat)
        tag.putBoolean(DataKeys.STADIUM_BLOCK_CONFIGURED, isConfigured)

        // Selected default formats
        val defaultFormatsTag = ListTag()
        for (format in selectedDefaultFormats) {
            defaultFormatsTag.add(StringTag.valueOf(format))
        }
        tag.put(DataKeys.STADIUM_BLOCK_SELECTED_DEFAULT_FORMATS, defaultFormatsTag)

        // Format configs
        val formatsTag = CompoundTag()
        for ((key, config) in formatConfigs) {
            formatsTag.put(key, config.toNbt())
        }
        tag.put(DataKeys.STADIUM_BLOCK_FORMAT_CONFIGS, formatsTag)

        // Layouts
        singlesLayout?.let { tag.put(DataKeys.STADIUM_BLOCK_SINGLES_LAYOUT, it.toNbt()) }
        doublesLayout?.let { tag.put(DataKeys.STADIUM_BLOCK_DOUBLES_LAYOUT, it.toNbt()) }

        // Camera
        cameraTimeline?.let { tag.putString(DataKeys.STADIUM_BLOCK_CAMERA_TIMELINE, it.toString()) }
        cameraTimelineSideB?.let { tag.putString(DataKeys.STADIUM_BLOCK_CAMERA_TIMELINE_SIDE_B, it.toString()) }
        putVec3(tag, DataKeys.STADIUM_BLOCK_CAMERA_OFFSET, cameraOriginOffset)

        // Music
        introMusic?.let { tag.putString(DataKeys.STADIUM_BLOCK_INTRO_MUSIC, it.toString()) }
        tag.putFloat(DataKeys.STADIUM_BLOCK_INTRO_MUSIC_VOLUME, introMusicVolume)
        tag.putFloat(DataKeys.STADIUM_BLOCK_INTRO_MUSIC_PITCH, introMusicPitch)
        loopMusic?.let { tag.putString(DataKeys.STADIUM_BLOCK_LOOP_MUSIC, it.toString()) }
        tag.putFloat(DataKeys.STADIUM_BLOCK_LOOP_MUSIC_VOLUME, loopMusicVolume)
        tag.putFloat(DataKeys.STADIUM_BLOCK_LOOP_MUSIC_PITCH, loopMusicPitch)

        return tag
    }

    companion object {
        fun createDefault(stadiumId: UUID = UUID.randomUUID()): StadiumBlockData {
            return StadiumBlockData(
                stadiumId = stadiumId,
                name = "Stadium",
                ownerUUID = null,
                formatConfigs = mutableMapOf(),
                activeFormat = "",
                selectedDefaultFormats = mutableSetOf(),
                singlesLayout = null,
                doublesLayout = null,
                cameraTimeline = null,
                cameraTimelineSideB = null,
                cameraOriginOffset = Vec3.ZERO,
                introMusic = null,
                introMusicVolume = 1.0f,
                introMusicPitch = 1.0f,
                loopMusic = null,
                loopMusicVolume = 1.0f,
                loopMusicPitch = 1.0f,
                isConfigured = false
            )
        }

        fun fromNbt(tag: CompoundTag): StadiumBlockData {
            val stadiumId = if (tag.hasUUID(DataKeys.STADIUM_BLOCK_ID)) tag.getUUID(DataKeys.STADIUM_BLOCK_ID) else UUID.randomUUID()
            val name = tag.getString(DataKeys.STADIUM_BLOCK_NAME).ifEmpty { "Stadium" }
            val ownerUUID = if (tag.hasUUID(DataKeys.STADIUM_BLOCK_OWNER)) tag.getUUID(DataKeys.STADIUM_BLOCK_OWNER) else null
            val activeFormat = tag.getString(DataKeys.STADIUM_BLOCK_ACTIVE_FORMAT)
            val isConfigured = tag.getBoolean(DataKeys.STADIUM_BLOCK_CONFIGURED)

            // Selected default formats
            val selectedDefaultFormats = mutableSetOf<String>()
            if (tag.contains(DataKeys.STADIUM_BLOCK_SELECTED_DEFAULT_FORMATS, Tag.TAG_LIST.toInt())) {
                val list = tag.getList(DataKeys.STADIUM_BLOCK_SELECTED_DEFAULT_FORMATS, Tag.TAG_STRING.toInt())
                for (i in 0 until list.size) {
                    selectedDefaultFormats.add(list.getString(i))
                }
            }

            // Format configs
            val formatConfigs = mutableMapOf<String, StadiumFormatConfig>()
            if (tag.contains(DataKeys.STADIUM_BLOCK_FORMAT_CONFIGS, Tag.TAG_COMPOUND.toInt())) {
                val formatsTag = tag.getCompound(DataKeys.STADIUM_BLOCK_FORMAT_CONFIGS)
                for (key in formatsTag.allKeys) {
                    formatConfigs[key] = StadiumFormatConfig.fromNbt(formatsTag.getCompound(key))
                }
            }

            // New layout fields
            val singlesLayout = if (tag.contains(DataKeys.STADIUM_BLOCK_SINGLES_LAYOUT, Tag.TAG_COMPOUND.toInt()))
                StadiumBlockLayout.fromNbt(tag.getCompound(DataKeys.STADIUM_BLOCK_SINGLES_LAYOUT))
            else null

            val doublesLayout = if (tag.contains(DataKeys.STADIUM_BLOCK_DOUBLES_LAYOUT, Tag.TAG_COMPOUND.toInt()))
                StadiumBlockLayout.fromNbt(tag.getCompound(DataKeys.STADIUM_BLOCK_DOUBLES_LAYOUT))
            else null

            // Backward compatibility: migrate old player1Pos/player2Pos into singlesLayout
            val migratedSinglesLayout = if (singlesLayout == null) {
                val player1Pos = getVec3(tag, DataKeys.STADIUM_BLOCK_PLAYER1_POS)
                val player2Pos = getVec3(tag, DataKeys.STADIUM_BLOCK_PLAYER2_POS)
                if (player1Pos != null || player2Pos != null) {
                    StadiumBlockLayout(
                        side1 = StadiumBlockSide(
                            actors = player1Pos?.let { mutableListOf(StadiumBlockPlacement(position = it, lookAt = player2Pos)) } ?: mutableListOf(),
                            pokemon = mutableListOf()
                        ),
                        side2 = StadiumBlockSide(
                            actors = player2Pos?.let { mutableListOf(StadiumBlockPlacement(position = it, lookAt = player1Pos)) } ?: mutableListOf(),
                            pokemon = mutableListOf()
                        )
                    )
                } else null
            } else singlesLayout

            // Camera
            val cameraTimeline = tag.getString(DataKeys.STADIUM_BLOCK_CAMERA_TIMELINE).takeIf { it.isNotEmpty() }?.let { ResourceLocation.parse(it) }
            val cameraTimelineSideB = tag.getString(DataKeys.STADIUM_BLOCK_CAMERA_TIMELINE_SIDE_B).takeIf { it.isNotEmpty() }?.let { ResourceLocation.parse(it) }
            val cameraOriginOffset = getVec3(tag, DataKeys.STADIUM_BLOCK_CAMERA_OFFSET) ?: Vec3.ZERO

            // Music
            val introMusic = tag.getString(DataKeys.STADIUM_BLOCK_INTRO_MUSIC).takeIf { it.isNotEmpty() }?.let { ResourceLocation.parse(it) }
            val introMusicVolume = if (tag.contains(DataKeys.STADIUM_BLOCK_INTRO_MUSIC_VOLUME)) tag.getFloat(DataKeys.STADIUM_BLOCK_INTRO_MUSIC_VOLUME) else 1.0f
            val introMusicPitch = if (tag.contains(DataKeys.STADIUM_BLOCK_INTRO_MUSIC_PITCH)) tag.getFloat(DataKeys.STADIUM_BLOCK_INTRO_MUSIC_PITCH) else 1.0f
            val loopMusic = tag.getString(DataKeys.STADIUM_BLOCK_LOOP_MUSIC).takeIf { it.isNotEmpty() }?.let { ResourceLocation.parse(it) }
            val loopMusicVolume = if (tag.contains(DataKeys.STADIUM_BLOCK_LOOP_MUSIC_VOLUME)) tag.getFloat(DataKeys.STADIUM_BLOCK_LOOP_MUSIC_VOLUME) else 1.0f
            val loopMusicPitch = if (tag.contains(DataKeys.STADIUM_BLOCK_LOOP_MUSIC_PITCH)) tag.getFloat(DataKeys.STADIUM_BLOCK_LOOP_MUSIC_PITCH) else 1.0f

            return StadiumBlockData(
                stadiumId = stadiumId,
                name = name,
                ownerUUID = ownerUUID,
                formatConfigs = formatConfigs,
                activeFormat = activeFormat,
                selectedDefaultFormats = selectedDefaultFormats,
                singlesLayout = migratedSinglesLayout,
                doublesLayout = doublesLayout,
                cameraTimeline = cameraTimeline,
                cameraTimelineSideB = cameraTimelineSideB,
                cameraOriginOffset = cameraOriginOffset,
                introMusic = introMusic,
                introMusicVolume = introMusicVolume,
                introMusicPitch = introMusicPitch,
                loopMusic = loopMusic,
                loopMusicVolume = loopMusicVolume,
                loopMusicPitch = loopMusicPitch,
                isConfigured = isConfigured
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
    val allowedGimmicks: MutableSet<String>
) {
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
                allowedGimmicks = fromStringList(tag, DataKeys.STADIUM_FORMAT_ALLOWED_GIMMICKS)
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

// Shared NBT helpers for placement/layout serialization
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
