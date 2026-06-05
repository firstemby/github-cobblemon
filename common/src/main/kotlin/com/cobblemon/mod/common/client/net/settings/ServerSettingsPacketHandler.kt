/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.settings

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.client.settings.ServerSettings
import com.cobblemon.mod.common.net.messages.client.settings.ServerSettingsPacket
import net.minecraft.client.Minecraft

object ServerSettingsPacketHandler : ClientNetworkPacketHandler<ServerSettingsPacket> {
    override fun handle(packet: ServerSettingsPacket, client: Minecraft) {
        ServerSettings.preventCompletePartyDeposit = packet.preventCompletePartyDeposit
        ServerSettings.displayEntityLevelLabel = packet.displayEntityLevelLabel
        ServerSettings.displayEntityNameLabel = packet.displayEntityNameLabel
        ServerSettings.maxPokemonLevel = packet.maxPokemonLevel
        ServerSettings.maxPokemonFriendship = packet.maxPokemonFriendship
        ServerSettings.maxDynamaxLevel = packet.maxDynamaxLevel
        ServerSettings.unlockAllMoveDexMovesByDefault = packet.unlockAllMoveDexMovesByDefault
        ServerSettings.pokedexScannerPhotoRaycastDistance = packet.pokedexScannerPhotoRaycastDistance
        ServerSettings.pokedexScannerPhotoRaycastDensity = packet.pokedexScannerPhotoRaycastDensity
        ServerSettings.pokedexScannerPhotoPerimeterRaycastDensity = packet.pokedexScannerPhotoPerimeterRaycastDensity
        ServerSettings.pokedexScannerPhotoCoverageScoreCap = packet.pokedexScannerPhotoCoverageScoreCap
        ServerSettings.pokedexScannerPhotoCoverageScoreCapAtPercent = packet.pokedexScannerPhotoCoverageScoreCapAtPercent
        ServerSettings.pokedexScannerPhotoCenteredScoreCap = packet.pokedexScannerPhotoCenteredScoreCap
        ServerSettings.pokedexScannerPhotoFacingBonus = packet.pokedexScannerPhotoFacingBonus
        ServerSettings.pokedexScannerPhotoFullBodyBonus = packet.pokedexScannerPhotoFullBodyBonus
        ServerSettings.pokedexScannerPhotoOutOfFrameMultiplier = packet.pokedexScannerPhotoOutOfFrameMultiplier
        ServerSettings.pokedexScannerPhotoShinyBonus = packet.pokedexScannerPhotoShinyBonus
        ServerSettings.pokedexScannerPhotoUncommonSpeciesMultiplier = packet.pokedexScannerPhotoUncommonSpeciesMultiplier
        ServerSettings.pokedexScannerPhotoRareSpeciesMultiplier = packet.pokedexScannerPhotoRareSpeciesMultiplier
        ServerSettings.pokedexScannerPhotoUltraRareSpeciesMultiplier = packet.pokedexScannerPhotoUltraRareSpeciesMultiplier
        ServerSettings.pokedexScannerPhotoExtraPokemonBonusCap = packet.pokedexScannerPhotoExtraPokemonBonusCap
        ServerSettings.pokedexScannerPhotoExtraPokemonBonusPerSubject = packet.pokedexScannerPhotoExtraPokemonBonusPerSubject
        ServerSettings.pokedexScannerPhotoFamilyBonus = packet.pokedexScannerPhotoFamilyBonus
        ServerSettings.pokedexScannerPhotoHabitatBonus = packet.pokedexScannerPhotoHabitatBonus
        ServerSettings.pokedexScannerPhotoTimeOfDayBonus = packet.pokedexScannerPhotoTimeOfDayBonus
        ServerSettings.pokedexScannerPhotoWeatherBonus = packet.pokedexScannerPhotoWeatherBonus
        ServerSettings.pokedexScannerPhotoAlphaBonus = packet.pokedexScannerPhotoAlphaBonus
        ServerSettings.pokedexScannerPhotoWildBonus = packet.pokedexScannerPhotoWildBonus
        ServerSettings.pokedexScannerPhotoFormBonus = packet.pokedexScannerPhotoFormBonus
    }

}
