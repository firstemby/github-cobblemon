/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.settings

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.client.net.settings.ServerSettingsPacketHandler
import com.cobblemon.mod.common.net.messages.client.settings.ServerSettingsPacket

/**
 * A holder for config options the server wants to sync with the client.
 * See [ServerSettingsPacket] & [ServerSettingsPacketHandler] for more information.
 *
 * @author Licious
 * @since September 27th, 2022
 */
object ServerSettings {

    var preventCompletePartyDeposit = Cobblemon.config.preventCompletePartyDeposit
    var displayEntityLevelLabel = Cobblemon.config.displayEntityLevelLabel
    var displayEntityNameLabel = Cobblemon.config.displayEntityNameLabel
    var maxPokemonLevel = Cobblemon.config.maxPokemonLevel
    var maxPokemonFriendship = Cobblemon.config.maxPokemonFriendship
    var maxDynamaxLevel = Cobblemon.config.maxDynamaxLevel
    var unlockAllMoveDexMovesByDefault = Cobblemon.config.unlockAllMoveDexMovesByDefault
    var pokedexScannerPhotoRaycastDistance = Cobblemon.config.pokedexScannerPhotoRaycastDistance
    var pokedexScannerPhotoRaycastDensity = Cobblemon.config.pokedexScannerPhotoRaycastDensity
    var pokedexScannerPhotoPerimeterRaycastDensity = Cobblemon.config.pokedexScannerPhotoPerimeterRaycastDensity
    var pokedexScannerPhotoCoverageScoreCap = Cobblemon.config.pokedexScannerPhotoCoverageScoreCap
    var pokedexScannerPhotoCoverageScoreCapAtPercent = Cobblemon.config.pokedexScannerPhotoCoverageScoreCapAtPercent
    var pokedexScannerPhotoCenteredScoreCap = Cobblemon.config.pokedexScannerPhotoCenteredScoreCap
    var pokedexScannerPhotoFacingBonus = Cobblemon.config.pokedexScannerPhotoFacingBonus
    var pokedexScannerPhotoFullBodyBonus = Cobblemon.config.pokedexScannerPhotoFullBodyBonus
    var pokedexScannerPhotoOutOfFrameMultiplier = Cobblemon.config.pokedexScannerPhotoOutOfFrameMultiplier
    var pokedexScannerPhotoShinyBonus = Cobblemon.config.pokedexScannerPhotoShinyBonus
    var pokedexScannerPhotoUncommonSpeciesMultiplier = Cobblemon.config.pokedexScannerPhotoUncommonSpeciesMultiplier
    var pokedexScannerPhotoRareSpeciesMultiplier = Cobblemon.config.pokedexScannerPhotoRareSpeciesMultiplier
    var pokedexScannerPhotoUltraRareSpeciesMultiplier = Cobblemon.config.pokedexScannerPhotoUltraRareSpeciesMultiplier
    var pokedexScannerPhotoExtraPokemonBonusCap = Cobblemon.config.pokedexScannerPhotoExtraPokemonBonusCap
    var pokedexScannerPhotoExtraPokemonBonusPerSubject = Cobblemon.config.pokedexScannerPhotoExtraPokemonBonusPerSubject
    var pokedexScannerPhotoFamilyBonus = Cobblemon.config.pokedexScannerPhotoFamilyBonus
    var pokedexScannerPhotoHabitatBonus = Cobblemon.config.pokedexScannerPhotoHabitatBonus
    var pokedexScannerPhotoTimeOfDayBonus = Cobblemon.config.pokedexScannerPhotoTimeOfDayBonus
    var pokedexScannerPhotoWeatherBonus = Cobblemon.config.pokedexScannerPhotoWeatherBonus
    var pokedexScannerPhotoAlphaBonus = Cobblemon.config.pokedexScannerPhotoAlphaBonus
    var pokedexScannerPhotoWildBonus = Cobblemon.config.pokedexScannerPhotoWildBonus
    var pokedexScannerPhotoFormBonus = Cobblemon.config.pokedexScannerPhotoFormBonus

}
