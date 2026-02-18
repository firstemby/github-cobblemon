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
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3

data class StadiumBlockData(
    val stadiumId: UUID,
    var name: String,
    var ownerUUID: UUID?,
    val formatConfigs: MutableMap<String, StadiumFormatConfig>,
    var activeFormat: String,
    var selectedDefaultFormats: MutableSet<String>,
    var corner1: BlockPos?,
    var corner2: BlockPos?,
    var player1Pos: Vec3?,
    var player2Pos: Vec3?,
    var cameraTimeline: ResourceLocation?,
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

        // Arena positions
        corner1?.let { putBlockPos(tag, DataKeys.STADIUM_BLOCK_CORNER1, it) }
        corner2?.let { putBlockPos(tag, DataKeys.STADIUM_BLOCK_CORNER2, it) }
        player1Pos?.let { putVec3(tag, DataKeys.STADIUM_BLOCK_PLAYER1_POS, it) }
        player2Pos?.let { putVec3(tag, DataKeys.STADIUM_BLOCK_PLAYER2_POS, it) }

        // Camera
        cameraTimeline?.let { tag.putString(DataKeys.STADIUM_BLOCK_CAMERA_TIMELINE, it.toString()) }
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
                corner1 = null,
                corner2 = null,
                player1Pos = null,
                player2Pos = null,
                cameraTimeline = null,
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

            // Arena positions
            val corner1 = getBlockPos(tag, DataKeys.STADIUM_BLOCK_CORNER1)
            val corner2 = getBlockPos(tag, DataKeys.STADIUM_BLOCK_CORNER2)
            val player1Pos = getVec3(tag, DataKeys.STADIUM_BLOCK_PLAYER1_POS)
            val player2Pos = getVec3(tag, DataKeys.STADIUM_BLOCK_PLAYER2_POS)

            // Camera
            val cameraTimeline = tag.getString(DataKeys.STADIUM_BLOCK_CAMERA_TIMELINE).takeIf { it.isNotEmpty() }?.let { ResourceLocation.parse(it) }
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
                corner1 = corner1,
                corner2 = corner2,
                player1Pos = player1Pos,
                player2Pos = player2Pos,
                cameraTimeline = cameraTimeline,
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

        private fun putBlockPos(tag: CompoundTag, key: String, pos: BlockPos) {
            val posTag = CompoundTag()
            posTag.putInt("X", pos.x)
            posTag.putInt("Y", pos.y)
            posTag.putInt("Z", pos.z)
            tag.put(key, posTag)
        }

        private fun getBlockPos(tag: CompoundTag, key: String): BlockPos? {
            if (!tag.contains(key, Tag.TAG_COMPOUND.toInt())) return null
            val posTag = tag.getCompound(key)
            return BlockPos(posTag.getInt("X"), posTag.getInt("Y"), posTag.getInt("Z"))
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
    val side1PokemonPositions: MutableList<Vec3>,
    val side2PokemonPositions: MutableList<Vec3>
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
        tag.put(DataKeys.STADIUM_FORMAT_SIDE1_POKEMON_POS, toVec3List(side1PokemonPositions))
        tag.put(DataKeys.STADIUM_FORMAT_SIDE2_POKEMON_POS, toVec3List(side2PokemonPositions))
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
                side1PokemonPositions = fromVec3List(tag, DataKeys.STADIUM_FORMAT_SIDE1_POKEMON_POS),
                side2PokemonPositions = fromVec3List(tag, DataKeys.STADIUM_FORMAT_SIDE2_POKEMON_POS)
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

        private fun toVec3List(positions: List<Vec3>): ListTag {
            val list = ListTag()
            for (vec in positions) {
                val vecTag = CompoundTag()
                vecTag.putDouble("X", vec.x)
                vecTag.putDouble("Y", vec.y)
                vecTag.putDouble("Z", vec.z)
                list.add(vecTag)
            }
            return list
        }

        private fun fromVec3List(tag: CompoundTag, key: String): MutableList<Vec3> {
            val positions = mutableListOf<Vec3>()
            if (tag.contains(key, Tag.TAG_LIST.toInt())) {
                val list = tag.getList(key, Tag.TAG_COMPOUND.toInt())
                for (i in 0 until list.size) {
                    val vecTag = list.getCompound(i)
                    positions.add(Vec3(vecTag.getDouble("X"), vecTag.getDouble("Y"), vecTag.getDouble("Z")))
                }
            }
            return positions
        }
    }
}
