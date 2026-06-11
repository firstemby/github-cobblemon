/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.settings

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * A packet that will sync simple config settings to the client that shouldn't require to be data pack powered.
 *
 * @author Licious
 * @since September 25th, 2022
 */
class ServerSettingsPacket internal constructor(
    val preventCompletePartyDeposit: Boolean,
    val displayEntityLevelLabel: Boolean,
    val displayEntityNameLabel: Boolean,
    val maxPokemonLevel: Int,
    val maxPokemonFriendship: Int,
    val maxDynamaxLevel: Int,
    val unlockAllMoveDexMovesByDefault: Boolean,
    val pokemonIntrinsicSizeMin: Float,
    val pokemonIntrinsicSizeMax: Float,
    val pokedexScannerPhotoRaycastDistance: Double,
    val pokedexScannerPhotoRaycastDensity: Int,
    val pokedexScannerPhotoPerimeterRaycastDensity: Int,
    val pokedexScannerPhotoCoverageScoreCap: Int,
    val pokedexScannerPhotoCoverageScoreCapAtPercent: Float,
    val pokedexScannerPhotoCenteredScoreCap: Int,
    val pokedexScannerPhotoFacingBonus: Int,
    val pokedexScannerPhotoFullBodyBonus: Int,
    val pokedexScannerPhotoOutOfFrameMultiplier: Float,
    val pokedexScannerPhotoShinyBonus: Int,
    val pokedexScannerPhotoUncommonSpeciesMultiplier: Float,
    val pokedexScannerPhotoRareSpeciesMultiplier: Float,
    val pokedexScannerPhotoUltraRareSpeciesMultiplier: Float,
    val pokedexScannerPhotoExtraPokemonBonusCap: Int,
    val pokedexScannerPhotoExtraPokemonBonusPerSubject: Int,
    val pokedexScannerPhotoFamilyBonus: Int,
    val pokedexScannerPhotoHabitatBonus: Int,
    val pokedexScannerPhotoTimeOfDayBonus: Int,
    val pokedexScannerPhotoWeatherBonus: Int,
    val pokedexScannerPhotoAlphaBonus: Int,
    val pokedexScannerPhotoWildBonus: Int,
    val pokedexScannerPhotoFormBonus: Int
) : NetworkPacket<ServerSettingsPacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBoolean(Cobblemon.config.preventCompletePartyDeposit)
        buffer.writeBoolean(Cobblemon.config.displayEntityLevelLabel)
        buffer.writeBoolean(Cobblemon.config.displayEntityNameLabel)
        buffer.writeInt(Cobblemon.config.maxPokemonLevel)
        buffer.writeInt(Cobblemon.config.maxPokemonFriendship)
        buffer.writeInt(Cobblemon.config.maxDynamaxLevel)
        buffer.writeBoolean(Cobblemon.config.unlockAllMoveDexMovesByDefault)
        buffer.writeFloat(Cobblemon.config.pokemonIntrinsicSizeMin)
        buffer.writeFloat(Cobblemon.config.pokemonIntrinsicSizeMax)
        buffer.writeDouble(Cobblemon.config.pokedexScannerPhotoRaycastDistance)
        buffer.writeInt(Cobblemon.config.pokedexScannerPhotoRaycastDensity)
        buffer.writeInt(Cobblemon.config.pokedexScannerPhotoPerimeterRaycastDensity)
        buffer.writeInt(Cobblemon.config.pokedexScannerPhotoCoverageScoreCap)
        buffer.writeFloat(Cobblemon.config.pokedexScannerPhotoCoverageScoreCapAtPercent)
        buffer.writeInt(Cobblemon.config.pokedexScannerPhotoCenteredScoreCap)
        buffer.writeInt(Cobblemon.config.pokedexScannerPhotoFacingBonus)
        buffer.writeInt(Cobblemon.config.pokedexScannerPhotoFullBodyBonus)
        buffer.writeFloat(Cobblemon.config.pokedexScannerPhotoOutOfFrameMultiplier)
        buffer.writeInt(Cobblemon.config.pokedexScannerPhotoShinyBonus)
        buffer.writeFloat(Cobblemon.config.pokedexScannerPhotoUncommonSpeciesMultiplier)
        buffer.writeFloat(Cobblemon.config.pokedexScannerPhotoRareSpeciesMultiplier)
        buffer.writeFloat(Cobblemon.config.pokedexScannerPhotoUltraRareSpeciesMultiplier)
        buffer.writeInt(Cobblemon.config.pokedexScannerPhotoExtraPokemonBonusCap)
        buffer.writeInt(Cobblemon.config.pokedexScannerPhotoExtraPokemonBonusPerSubject)
        buffer.writeInt(Cobblemon.config.pokedexScannerPhotoFamilyBonus)
        buffer.writeInt(Cobblemon.config.pokedexScannerPhotoHabitatBonus)
        buffer.writeInt(Cobblemon.config.pokedexScannerPhotoTimeOfDayBonus)
        buffer.writeInt(Cobblemon.config.pokedexScannerPhotoWeatherBonus)
        buffer.writeInt(Cobblemon.config.pokedexScannerPhotoAlphaBonus)
        buffer.writeInt(Cobblemon.config.pokedexScannerPhotoWildBonus)
        buffer.writeInt(Cobblemon.config.pokedexScannerPhotoFormBonus)
    }
    companion object {
        val ID = cobblemonResource("server_settings")
        fun decode(buffer: RegistryFriendlyByteBuf) = ServerSettingsPacket(
            buffer.readBoolean(), // preventCompletePartyDeposit
            buffer.readBoolean(), // displayEntityLevelLabel
            buffer.readBoolean(), // displayEntityNameLabel
            buffer.readInt(),     // maxPokemonLevel
            buffer.readInt(),     // maxPokemonFriendship
            buffer.readInt(),     // maxDynamaxLevel
            buffer.readBoolean(), // unlockAllMoveDexMovesByDefault
            buffer.readFloat(),   // pokemonIntrinsicSizeMin
            buffer.readFloat(),   // pokemonIntrinsicSizeMax
            buffer.readDouble(),  // pokedexScannerPhotoRaycastDistance
            buffer.readInt(),     // pokedexScannerPhotoRaycastDensity
            buffer.readInt(),     // pokedexScannerPhotoPerimeterRaycastDensity
            buffer.readInt(),     // pokedexScannerPhotoCoverageScoreCap
            buffer.readFloat(),   // pokedexScannerPhotoCoverageScoreCapAtPercent
            buffer.readInt(),     // pokedexScannerPhotoCenteredScoreCap
            buffer.readInt(),     // pokedexScannerPhotoFacingBonus
            buffer.readInt(),     // pokedexScannerPhotoFullBodyBonus
            buffer.readFloat(),   // pokedexScannerPhotoOutOfFrameMultiplier
            buffer.readInt(),     // pokedexScannerPhotoShinyBonus
            buffer.readFloat(),   // pokedexScannerPhotoUncommonSpeciesMultiplier
            buffer.readFloat(),   // pokedexScannerPhotoRareSpeciesMultiplier
            buffer.readFloat(),   // pokedexScannerPhotoUltraRareSpeciesMultiplier
            buffer.readInt(),     // pokedexScannerPhotoExtraPokemonBonusCap
            buffer.readInt(),     // pokedexScannerPhotoExtraPokemonBonusPerSubject
            buffer.readInt(),     // pokedexScannerPhotoFamilyBonus
            buffer.readInt(),     // pokedexScannerPhotoHabitatBonus
            buffer.readInt(),     // pokedexScannerPhotoTimeOfDayBonus
            buffer.readInt(),     // pokedexScannerPhotoWeatherBonus
            buffer.readInt(),     // pokedexScannerPhotoAlphaBonus
            buffer.readInt(),     // pokedexScannerPhotoWildBonus
            buffer.readInt()      // pokedexScannerPhotoFormBonus
        )
    }
}
