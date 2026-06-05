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
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.ui.PokedexPhotoUploadPacket
import com.cobblemon.mod.common.net.messages.client.ui.PokedexPhotoUploadResultPacket
import com.cobblemon.mod.common.pokedex.photo.PokedexPhotoQuality
import com.cobblemon.mod.common.pokedex.photo.PokedexPhotoRepository
import com.cobblemon.mod.common.pokedex.photo.PokedexPhotoSubjectQuality
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.ai.behavior.EntityTracker
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import kotlin.math.max
import kotlin.math.roundToInt

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
        val safeSubjects = packet.qualitySubjects
            .asSequence()
            .mapNotNull { subject ->
                val speciesId = ResourceLocation.tryParse(subject.species) ?: return@mapNotNull null
                val species = speciesId.toString()
                if (species !in detectedSpecies) return@mapNotNull null
                PokedexPhotoSubjectQuality(
                    species = species,
                    entityUuid = subject.entityUuid,
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
                    rarityMultiplier = PokedexPhotoQuality.speciesRarityMultiplier(speciesId)
                )
            }
            .take(64)
            .toList()
        val adjustedSubjects = applyServerAuthoritativeLookScores(safeSubjects, player)
        val safeQuality = PokedexPhotoQuality.applySpeciesMultiplier(
            adjustedSubjects.firstOrNull { it.primarySubject }?.subjectScore ?: packet.qualityScore,
            detectedSpecies
        )

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
            qualitySubjects = adjustedSubjects
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

    private fun applyServerAuthoritativeLookScores(
        subjects: List<PokedexPhotoSubjectQuality>,
        player: ServerPlayer
    ): List<PokedexPhotoSubjectQuality> {
        if (subjects.isEmpty()) return emptyList()
        val facingBonus = Cobblemon.config.pokedexScannerPhotoFacingBonus.coerceIn(0, 5000)
        val withServerLookScores = subjects.map { subject ->
            subject.copy(
                directionScore = if (isBrainLookingAtPlayer(subject, player)) facingBonus else 0,
                primarySubject = false,
                fullBodyScore = 0,
                otherPokemonScore = 0,
                wholeBodyInFrame = true,
                clippingMultiplier = 1F
            )
        }
        val primary = withServerLookScores.maxWithOrNull(
            compareBy<PokedexPhotoSubjectQuality> { it.rayHits }
                .thenBy { it.rayCoverage }
                .thenBy { -it.centerDistance }
        ) ?: return emptyList()
        val otherPokemonBonus = calculateOtherPokemonBonus(primary, withServerLookScores)

        return withServerLookScores.map { subject ->
            if (subject === primary) {
                scorePrimarySubject(subject, otherPokemonBonus)
            } else {
                subject.copy(subjectScore = calculateSecondaryPokemonBonus(subject))
            }
        }.sortedWith(compareByDescending<PokedexPhotoSubjectQuality> { it.primarySubject }.thenByDescending { it.subjectScore })
    }

    private fun scorePrimarySubject(subject: PokedexPhotoSubjectQuality, otherPokemonBonus: Int): PokedexPhotoSubjectQuality {
        val wholeBodyInFrame = subject.perimeterHits == 0
        val sizeScore = calculateSizeScore(subject.rayCoverage.toDouble(), wholeBodyInFrame)
        val fullBodyScore = if (wholeBodyInFrame) Cobblemon.config.pokedexScannerPhotoFullBodyBonus.coerceAtLeast(0) else 0
        val clippingMultiplier = if (wholeBodyInFrame) {
            1.0
        } else {
            Cobblemon.config.pokedexScannerPhotoOutOfFrameMultiplier.coerceIn(0F, 1F).toDouble()
        }
        val rawScore = sizeScore + subject.placementScore + subject.directionScore + subject.poseScore + fullBodyScore + otherPokemonBonus
        val subjectScore = (rawScore * clippingMultiplier * subject.shinyMultiplier)
            .roundToInt()
            .coerceIn(0, 5000)

        return subject.copy(
            primarySubject = true,
            subjectScore = subjectScore,
            sizeScore = sizeScore,
            fullBodyScore = fullBodyScore,
            otherPokemonScore = otherPokemonBonus,
            wholeBodyInFrame = wholeBodyInFrame,
            clippingMultiplier = clippingMultiplier.toFloat()
        )
    }

    private fun isBrainLookingAtPlayer(subject: PokedexPhotoSubjectQuality, player: ServerPlayer): Boolean {
        val entityUuid = subject.entityUuid ?: return false
        val entity = player.serverLevel().getEntity(entityUuid) as? PokemonEntity ?: return false
        val lookTarget = entity.brain
            .getMemory(MemoryModuleType.LOOK_TARGET)
            .orElse(null)
            ?: return false
        val targetEntity = (lookTarget as? EntityTracker)?.entity ?: return false
        return targetEntity === player || targetEntity.id == player.id || targetEntity.uuid == player.uuid
    }

    private fun calculateOtherPokemonBonus(
        primary: PokedexPhotoSubjectQuality,
        subjects: List<PokedexPhotoSubjectQuality>
    ): Int {
        val rawBonus = subjects
            .asSequence()
            .filter { it !== primary }
            .sumOf { subject -> calculateSecondaryPokemonBonus(subject) + calculateRelatedPokemonBonus(primary, subject) }
        return rawBonus.coerceIn(0, Cobblemon.config.pokedexScannerPhotoExtraPokemonBonusCap.coerceAtLeast(0))
    }

    private fun calculateSecondaryPokemonBonus(subject: PokedexPhotoSubjectQuality): Int {
        if (subject.rayHits <= 0) return 0
        val visibility = (subject.rayCoverage / 0.08F).coerceIn(0.25F, 1F)
        val placement = (0.35F + (subject.placementScore / 1000F)).coerceIn(0.35F, 1F)
        val scoreCap = Cobblemon.config.pokedexScannerPhotoExtraPokemonBonusPerSubject.coerceAtLeast(0)
        return (scoreCap * visibility * placement).roundToInt().coerceIn(0, scoreCap)
    }

    private fun calculateRelatedPokemonBonus(primary: PokedexPhotoSubjectQuality, subject: PokedexPhotoSubjectQuality): Int {
        if (primary.species == subject.species) {
            return Cobblemon.config.pokedexScannerPhotoRelatedPokemonBonus.coerceAtLeast(0)
        }
        return if (areSpeciesInSameEvolutionLine(primary.species, subject.species)) {
            Cobblemon.config.pokedexScannerPhotoRelatedPokemonBonus.coerceAtLeast(0)
        } else {
            0
        }
    }

    private fun areSpeciesInSameEvolutionLine(firstSpecies: String, secondSpecies: String): Boolean {
        val firstId = ResourceLocation.tryParse(firstSpecies) ?: return false
        val secondId = ResourceLocation.tryParse(secondSpecies) ?: return false
        if (firstId == secondId) return true
        return secondId in collectEvolutionFamily(firstId)
    }

    private fun collectEvolutionFamily(speciesId: ResourceLocation): Set<ResourceLocation> {
        val rootSpecies = PokemonSpecies.getByIdentifier(speciesId) ?: return setOf(speciesId)
        val family = linkedSetOf<ResourceLocation>()
        val processed = hashSetOf<ResourceLocation>()
        val queue = ArrayDeque<Species>()
        queue.add(rootSpecies)
        var preEvolution = rootSpecies.preEvolution?.species
        while (preEvolution != null && family.add(preEvolution.resourceIdentifier)) {
            queue.add(preEvolution)
            preEvolution = preEvolution.preEvolution?.species
        }

        while (queue.isNotEmpty()) {
            val species = queue.removeFirst()
            if (!processed.add(species.resourceIdentifier)) continue
            family.add(species.resourceIdentifier)
            species.evolutions
                .asSequence()
                .mapNotNull { evolution -> evolution.result.species?.asIdentifierDefaultingNamespace() }
                .mapNotNull(PokemonSpecies::getByIdentifier)
                .forEach(queue::add)
        }
        return family
    }

    private fun calculateSizeScore(rayCoverage: Double, wholeBodyInFrame: Boolean): Int {
        val scoreCap = Cobblemon.config.pokedexScannerPhotoCoverageScoreCap.coerceIn(0, 5000)
        val capAtCoverage = Cobblemon.config.pokedexScannerPhotoCoverageScoreCapAtPercent.toDouble().coerceIn(0.01, 1.0)
        val normalizedCoverage = (rayCoverage / capAtCoverage).coerceIn(0.0, 1.0)
        val coverageFactor = Math.pow(normalizedCoverage, 2.4)
        val tooLargeFactor = if (rayCoverage <= 0.55 || wholeBodyInFrame) {
            1.0
        } else {
            (1.0 - ((rayCoverage - 0.55) / 0.35 * 0.2)).coerceIn(0.8, 1.0)
        }
        return (scoreCap.toDouble() * coverageFactor * tooLargeFactor)
            .roundToInt()
            .coerceIn(0, scoreCap)
    }
}
