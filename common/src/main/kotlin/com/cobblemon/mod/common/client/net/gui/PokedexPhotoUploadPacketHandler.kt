/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.gui

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.net.messages.client.ui.PokedexPhotoUploadPacket
import com.cobblemon.mod.common.net.messages.client.ui.PokedexPhotoUploadResultPacket
import com.cobblemon.mod.common.pokedex.photo.PokedexPhotoQuality
import com.cobblemon.mod.common.pokedex.photo.PokedexPhotoRepository
import com.cobblemon.mod.common.pokedex.photo.PokedexPhotoSubjectQuality
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import kotlin.math.max

object PokedexPhotoUploadPacketHandler : ServerNetworkPacketHandler<PokedexPhotoUploadPacket> {
    private const val MAX_UPLOAD_SIZE = 5 * 1024 * 1024

    override fun handle(packet: PokedexPhotoUploadPacket, server: MinecraftServer, player: ServerPlayer) {
        val configuredResolution = max(64, Cobblemon.config.pokedexScannerPhotoResolution)
        val expectedRawSize = configuredResolution.toLong() * configuredResolution.toLong() * 4L
        val maxUploadSize = max(MAX_UPLOAD_SIZE.toLong(), expectedRawSize + (512L * 1024L))
            .coerceAtMost(32L * 1024L * 1024L)
            .toInt()

        if (packet.pngData.isEmpty() || packet.pngData.size > maxUploadSize) {
            PokedexPhotoUploadResultPacket(false, "Failed to save Pokedex photo: upload is invalid.").sendToPlayer(player)
            return
        }

        val maxSpeciesEntries = 256
        if (packet.detectedSpecies.size > maxSpeciesEntries) {
            PokedexPhotoUploadResultPacket(false, "Failed to save Pokedex photo: metadata is invalid.").sendToPlayer(player)
            return
        }

        val detectedSpecies = packet.detectedSpecies.asSequence()
            .mapNotNull(ResourceLocation::tryParse)
            .filter { PokemonSpecies.getByIdentifier(it) != null }
            .distinct()
            .take(maxSpeciesEntries)
            .map { it.toString() }
            .toList()

        val safeBiome = ResourceLocation.tryParse(packet.biome)?.toString() ?: "minecraft:unknown"
        val safeDimension = ResourceLocation.tryParse(packet.dimension)?.toString() ?: "minecraft:overworld"
        val safeTimeOfDayTicks = packet.timeOfDayTicks.coerceIn(0L, 23999L)
        val safeQuality = PokedexPhotoQuality.applySpeciesMultiplier(packet.qualityScore, detectedSpecies)
        val safeSubjects = packet.qualitySubjects
            .asSequence()
            .mapNotNull { subject ->
                val speciesId = ResourceLocation.tryParse(subject.species) ?: return@mapNotNull null
                val species = speciesId.toString()
                if (species !in detectedSpecies) return@mapNotNull null
                PokedexPhotoSubjectQuality(
                    species = species,
                    primarySubject = subject.primarySubject,
                    subjectScore = subject.subjectScore.coerceIn(0, 5000),
                    sizeScore = subject.sizeScore.coerceIn(0, Cobblemon.config.pokedexScannerPhotoCoverageScoreCap.coerceIn(0, 5000)),
                    placementScore = subject.placementScore.coerceIn(0, Cobblemon.config.pokedexScannerPhotoCenteredScoreCap.coerceIn(0, 5000)),
                    centerDistance = subject.centerDistance.coerceIn(0F, 1F),
                    distanceScore = subject.distanceScore.coerceIn(0, 1000),
                    directionScore = subject.directionScore.coerceIn(0, Cobblemon.config.pokedexScannerPhotoFacingBonus.coerceIn(0, 5000)),
                    poseScore = subject.poseScore.coerceIn(0, 800),
                    fullBodyScore = subject.fullBodyScore.coerceIn(0, Cobblemon.config.pokedexScannerPhotoFullBodyBonus.coerceAtLeast(0)),
                    otherPokemonScore = subject.otherPokemonScore.coerceIn(0, Cobblemon.config.pokedexScannerPhotoExtraPokemonBonusCap.coerceAtLeast(0)),
                    rayHits = subject.rayHits.coerceIn(0, 4096),
                    rayCoverage = subject.rayCoverage.coerceIn(0F, 1F),
                    perimeterHits = subject.perimeterHits.coerceIn(0, 4096),
                    wholeBodyInFrame = subject.wholeBodyInFrame,
                    clippingMultiplier = subject.clippingMultiplier.coerceIn(0F, 1F),
                    activeAnimations = subject.activeAnimations.take(16),
                    shiny = subject.shiny,
                    shinyMultiplier = subject.shinyMultiplier.coerceIn(1F, Cobblemon.config.pokedexScannerPhotoShinyMultiplier.coerceAtLeast(1F)),
                    animationMultiplier = subject.animationMultiplier.coerceIn(1F, 1.5F),
                    rarityMultiplier = PokedexPhotoQuality.speciesRarityMultiplier(speciesId)
                )
            }
            .take(64)
            .toList()

        val result = PokedexPhotoRepository.saveFromPng(
            owner = player.uuid,
            pngData = packet.pngData,
            detectedSpecies = detectedSpecies,
            biome = safeBiome,
            dimension = safeDimension,
            timeOfDayTicks = safeTimeOfDayTicks,
            qualityScore = safeQuality.score,
            qualityBaseScore = safeQuality.baseScore,
            qualitySpeciesMultiplier = safeQuality.speciesMultiplier,
            qualitySubjects = safeSubjects
        )
        if (result.success && result.photo != null) {
            val photoId = result.photo.id
            PokedexPhotoUploadResultPacket(
                true,
                "Saved Pokedex scan photo: $photoId | Score ${result.photo.qualityScore}/5000 (base ${result.photo.qualityBaseScore}, rarity x${"%.2f".format(result.photo.qualitySpeciesMultiplier)})"
            ).sendToPlayer(player)
            PokedexPhotoNetworkUtil.sendGallery(player)
        } else {
            val message = result.errorMessage ?: "Unknown save error."
            PokedexPhotoUploadResultPacket(false, "Failed to save Pokedex photo: $message").sendToPlayer(player)
        }
    }
}
