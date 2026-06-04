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
    val pokedexScannerPhotoRaycastDistance: Double,
    val pokedexScannerPhotoInnerRaycastDensity: Int,
    val pokedexScannerPhotoPerimeterRaycastDensity: Int,
    val pokedexScannerPhotoCoverageScoreCap: Int,
    val pokedexScannerPhotoCoverageScoreCapAtPercent: Float,
    val pokedexScannerPhotoCenteredScoreCap: Int,
    val pokedexScannerPhotoFacingScoreCap: Int,
    val pokedexScannerPhotoLookTargetBonus: Int,
    val pokedexScannerPhotoFullBodyBonus: Int,
    val pokedexScannerPhotoOutOfFrameMultiplier: Float,
    val pokedexScannerPhotoShinyMultiplier: Float,
    val pokedexScannerPhotoUncommonSpeciesMultiplier: Float,
    val pokedexScannerPhotoRareSpeciesMultiplier: Float,
    val pokedexScannerPhotoUltraRareSpeciesMultiplier: Float,
    val pokedexScannerPhotoExtraPokemonBonusCap: Int,
    val pokedexScannerPhotoExtraPokemonBonusPercentCap: Float,
    val pokedexScannerPhotoExtraPokemonBonusPerSubject: Int,
    val pokedexScannerPhotoSameSpeciesBonus: Int,
    val pokedexScannerPhotoEvolutionLineBonus: Int
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
        buffer.writeDouble(Cobblemon.config.pokedexScannerPhotoRaycastDistance)
        buffer.writeInt(Cobblemon.config.pokedexScannerPhotoInnerRaycastDensity)
        buffer.writeInt(Cobblemon.config.pokedexScannerPhotoPerimeterRaycastDensity)
        buffer.writeInt(Cobblemon.config.pokedexScannerPhotoCoverageScoreCap)
        buffer.writeFloat(Cobblemon.config.pokedexScannerPhotoCoverageScoreCapAtPercent)
        buffer.writeInt(Cobblemon.config.pokedexScannerPhotoCenteredScoreCap)
        buffer.writeInt(Cobblemon.config.pokedexScannerPhotoFacingScoreCap)
        buffer.writeInt(Cobblemon.config.pokedexScannerPhotoLookTargetBonus)
        buffer.writeInt(Cobblemon.config.pokedexScannerPhotoFullBodyBonus)
        buffer.writeFloat(Cobblemon.config.pokedexScannerPhotoOutOfFrameMultiplier)
        buffer.writeFloat(Cobblemon.config.pokedexScannerPhotoShinyMultiplier)
        buffer.writeFloat(Cobblemon.config.pokedexScannerPhotoUncommonSpeciesMultiplier)
        buffer.writeFloat(Cobblemon.config.pokedexScannerPhotoRareSpeciesMultiplier)
        buffer.writeFloat(Cobblemon.config.pokedexScannerPhotoUltraRareSpeciesMultiplier)
        buffer.writeInt(Cobblemon.config.pokedexScannerPhotoExtraPokemonBonusCap)
        buffer.writeFloat(Cobblemon.config.pokedexScannerPhotoExtraPokemonBonusPercentCap)
        buffer.writeInt(Cobblemon.config.pokedexScannerPhotoExtraPokemonBonusPerSubject)
        buffer.writeInt(Cobblemon.config.pokedexScannerPhotoSameSpeciesBonus)
        buffer.writeInt(Cobblemon.config.pokedexScannerPhotoEvolutionLineBonus)
    }
    companion object {
        val ID = cobblemonResource("server_settings")
        fun decode(buffer: RegistryFriendlyByteBuf) = ServerSettingsPacket(
            buffer.readBoolean(),
            buffer.readBoolean(),
            buffer.readBoolean(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readBoolean(),
            buffer.readDouble(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readFloat(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readFloat(),
            buffer.readFloat(),
            buffer.readFloat(),
            buffer.readFloat(),
            buffer.readFloat(),
            buffer.readInt(),
            buffer.readFloat(),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt()
        )
    }
}
