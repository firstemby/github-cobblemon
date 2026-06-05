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
import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.ui.PokedexPhotoUploadPacket
import com.cobblemon.mod.common.net.messages.client.ui.PokedexPhotoUploadResultPacket
import com.cobblemon.mod.common.pokedex.photo.PokedexPhotoQuality
import com.cobblemon.mod.common.pokedex.photo.PokedexPhotoRepository
import com.cobblemon.mod.common.pokedex.photo.PokedexPhotoSubjectQuality
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.ai.behavior.EntityTracker
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import kotlin.math.max
import kotlin.math.roundToInt

object PokedexPhotoUploadPacketHandler : ServerNetworkPacketHandler<PokedexPhotoUploadPacket> {
    private const val MAX_UPLOAD_SIZE = 5 * 1024 * 1024
    var showDebugPhotoScore = false

    private data class OtherPokemonBonuses(
        val extraPokemonScore: Int,
        val herdScore: Int
    )

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
        val safeBiomeId = ResourceLocation.tryParse(safeBiome)
        val safeDimension = ResourceLocation.tryParse(packet.dimension)?.toString() ?: "minecraft:overworld"
        val safeDimensionId = ResourceLocation.tryParse(safeDimension)
        val safeTimeOfDayTicks = packet.timeOfDayTicks.coerceIn(0L, 23999L)
        val safeRaining = packet.raining
        val safeThundering = packet.thundering
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
                    herdScore = subject.herdScore.coerceIn(0, 5000),
                    habitatScore = subject.habitatScore.coerceIn(0, Cobblemon.config.pokedexScannerPhotoHabitatBonus.coerceAtLeast(0)),
                    timeOfDayScore = subject.timeOfDayScore.coerceIn(0, Cobblemon.config.pokedexScannerPhotoTimeOfDayBonus.coerceAtLeast(0)),
                    weatherScore = subject.weatherScore.coerceIn(0, Cobblemon.config.pokedexScannerPhotoWeatherBonus.coerceAtLeast(0)),
                    shinyScore = subject.shinyScore.coerceIn(0, Cobblemon.config.pokedexScannerPhotoShinyBonus.coerceAtLeast(0)),
                    alphaScore = subject.alphaScore.coerceIn(0, Cobblemon.config.pokedexScannerPhotoAlphaBonus.coerceAtLeast(0)),
                    wildScore = subject.wildScore.coerceIn(0, Cobblemon.config.pokedexScannerPhotoWildBonus.coerceAtLeast(0)),
                    formScore = subject.formScore.coerceIn(0, Cobblemon.config.pokedexScannerPhotoFormBonus.coerceAtLeast(0)),
                    rayHits = subject.rayHits.coerceIn(0, 4096),
                    rayCoverage = subject.rayCoverage.coerceIn(0F, 1F),
                    perimeterHits = subject.perimeterHits.coerceIn(0, 4096),
                    wholeBodyInFrame = subject.wholeBodyInFrame,
                    clippingMultiplier = subject.clippingMultiplier.coerceIn(0F, 1F),
                    activeAnimations = subject.activeAnimations.take(16),
                    shiny = subject.shiny,
                    shinyMultiplier = 1F,
                    alpha = subject.alpha,
                    wild = subject.wild,
                    alternateForm = subject.alternateForm,
                    rarityMultiplier = PokedexPhotoQuality.speciesRarityMultiplier(speciesId)
                )
            }
            .take(64)
            .toList()
        val adjustedSubjects = applyServerAuthoritativeScores(
            subjects = safeSubjects,
            player = player,
            biome = safeBiomeId,
            dimension = safeDimensionId,
            timeOfDayTicks = safeTimeOfDayTicks,
            raining = safeRaining,
            thundering = safeThundering
        )
        val primarySubject = adjustedSubjects.firstOrNull { it.primarySubject }
        val safeBaseScore = calculateAdditiveSubtotal(primarySubject)
        val safeFinalScore = primarySubject?.subjectScore ?: 0
        val safeRarityMultiplier = primarySubject?.rarityMultiplier ?: 1F
        sendServerScoreDebug(player, adjustedSubjects, safeBaseScore, safeFinalScore)

        val result = PokedexPhotoRepository.saveFromPng(
            owner = player.uuid,
            pngData = packet.pngData,
            detectedSpecies = detectedSpecies,
            biome = safeBiome,
            dimension = safeDimension,
            timeOfDayTicks = safeTimeOfDayTicks,
            raining = safeRaining,
            thundering = safeThundering,
            qualityScore = safeFinalScore,
            qualityBaseScore = safeBaseScore,
            qualitySpeciesMultiplier = safeRarityMultiplier,
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

    private fun sendServerScoreDebug(
        player: ServerPlayer,
        subjects: List<PokedexPhotoSubjectQuality>,
        baseScore: Int,
        finalScore: Int
    ) {
        val primary = subjects.firstOrNull { it.primarySubject } ?: return
        if (showDebugPhotoScore)
            player.sendSystemMessage(
                Component.literal(
                    "[Photo Score Debug] server primary=${primary.species}, " +
                        "size +${primary.sizeScore}, center +${primary.placementScore}, " +
                        "extra +${primary.otherPokemonScore}, look-at-camera +${primary.directionScore}, herd +${primary.herdScore}, " +
                        "wild +${primary.wildScore}, form +${primary.formScore}, " +
                        "pose +${primary.poseScore}, habitat +${primary.habitatScore}, time +${primary.timeOfDayScore}, " +
                        "weather +${primary.weatherScore}, shiny +${primary.shinyScore}, alpha +${primary.alphaScore}, " +
                        "rarity x${"%.2f".format(primary.rarityMultiplier)}, too close x${"%.2f".format(primary.clippingMultiplier)}, " +
                        "lookTargetMatched=${primary.directionScore > 0}, base=$baseScore, final=$finalScore"
                )
            )
    }

    private fun applyServerAuthoritativeScores(
        subjects: List<PokedexPhotoSubjectQuality>,
        player: ServerPlayer,
        biome: ResourceLocation?,
        dimension: ResourceLocation?,
        timeOfDayTicks: Long,
        raining: Boolean,
        thundering: Boolean
    ): List<PokedexPhotoSubjectQuality> {
        if (subjects.isEmpty()) return emptyList()
        val facingBonus = Cobblemon.config.pokedexScannerPhotoFacingBonus.coerceIn(0, 5000)
        val withServerLookScores = subjects.map { subject ->
            subject.copy(
                directionScore = 0,
                primarySubject = false,
                fullBodyScore = 0,
                otherPokemonScore = 0,
                herdScore = 0,
                habitatScore = 0,
                timeOfDayScore = 0,
                weatherScore = 0,
                shinyScore = 0,
                alphaScore = 0,
                wildScore = 0,
                formScore = 0,
                wholeBodyInFrame = true,
                clippingMultiplier = 1F,
                shinyMultiplier = 1F
            )
        }
        val primary = withServerLookScores.maxWithOrNull(
            compareBy<PokedexPhotoSubjectQuality> { it.rayHits }
                .thenBy { it.rayCoverage }
                .thenBy { -it.centerDistance }
        ) ?: return emptyList()
        val primaryLookTargetMatched = isBrainLookingAtPlayer(primary, player)
        val otherPokemonBonuses = calculateOtherPokemonBonuses(primary, withServerLookScores)
        val entity = primary.entityUuid?.let { player.serverLevel().getEntity(it) } as? PokemonEntity
        val serverShiny = entity?.pokemon?.shiny ?: primary.shiny
        val serverAlpha = entity?.pokemon?.aspects?.contains("alpha") ?: primary.alpha
        val serverWild = entity?.pokemon?.isWild() ?: primary.wild
        val serverAlternateForm = entity?.pokemon?.let(::hasAlternateForm) ?: primary.alternateForm
        val speciesId = ResourceLocation.tryParse(primary.species)
        val spawnDetails = speciesId?.let(::spawnDetailsForSpecies).orEmpty()
        val habitatScore = if (biome != null && dimension != null && spawnDetails.any { detail -> detailAllowsBiomeAndDimension(detail, biome, dimension) }) {
            Cobblemon.config.pokedexScannerPhotoHabitatBonus.coerceAtLeast(0)
        } else {
            0
        }
        val timeOfDayScore = if (spawnDetails.any { detail -> detailAllowsTime(detail, timeOfDayTicks.toInt()) }) {
            Cobblemon.config.pokedexScannerPhotoTimeOfDayBonus.coerceAtLeast(0)
        } else {
            0
        }
        val weatherScore = if (spawnDetails.any { detail -> detailAllowsWeather(detail, raining, thundering) }) {
            Cobblemon.config.pokedexScannerPhotoWeatherBonus.coerceAtLeast(0)
        } else {
            0
        }

        return withServerLookScores.map { subject ->
            if (subject === primary) {
                scorePrimarySubject(
                    subject = subject.copy(
                        directionScore = if (primaryLookTargetMatched) facingBonus else 0,
                        habitatScore = habitatScore,
                        timeOfDayScore = timeOfDayScore,
                        weatherScore = weatherScore,
                        shiny = serverShiny,
                        shinyScore = if (serverShiny) Cobblemon.config.pokedexScannerPhotoShinyBonus.coerceAtLeast(0) else 0,
                        alpha = serverAlpha,
                        alphaScore = if (serverAlpha) Cobblemon.config.pokedexScannerPhotoAlphaBonus.coerceAtLeast(0) else 0,
                        wild = serverWild,
                        wildScore = if (serverWild) Cobblemon.config.pokedexScannerPhotoWildBonus.coerceAtLeast(0) else 0,
                        alternateForm = serverAlternateForm,
                        formScore = if (serverAlternateForm) Cobblemon.config.pokedexScannerPhotoFormBonus.coerceAtLeast(0) else 0,
                        rarityMultiplier = speciesId?.let(PokedexPhotoQuality::speciesRarityMultiplier) ?: 1F
                    ),
                    otherPokemonBonuses = otherPokemonBonuses
                )
            } else {
                subject.copy(subjectScore = 0)
            }
        }.sortedWith(compareByDescending<PokedexPhotoSubjectQuality> { it.primarySubject }.thenByDescending { it.subjectScore })
    }

    private fun scorePrimarySubject(subject: PokedexPhotoSubjectQuality, otherPokemonBonuses: OtherPokemonBonuses): PokedexPhotoSubjectQuality {
        val wholeBodyInFrame = subject.perimeterHits == 0
        val sizeScore = calculateSizeScore(subject.rayCoverage.toDouble(), wholeBodyInFrame)
        val clippingMultiplier = if (wholeBodyInFrame) {
            1.0
        } else {
            Cobblemon.config.pokedexScannerPhotoOutOfFrameMultiplier.coerceIn(0F, 1F).toDouble()
        }
        val additiveScore = sizeScore + subject.placementScore + otherPokemonBonuses.extraPokemonScore +
                subject.directionScore + otherPokemonBonuses.herdScore + subject.wildScore +
                subject.formScore + subject.poseScore +
                subject.habitatScore + subject.timeOfDayScore + subject.weatherScore +
                subject.shinyScore + subject.alphaScore
        val subjectScore = (additiveScore * subject.rarityMultiplier * clippingMultiplier)
            .roundToInt()
            .coerceIn(0, 5000)

        return subject.copy(
            primarySubject = true,
            subjectScore = subjectScore,
            sizeScore = sizeScore,
            fullBodyScore = 0,
            otherPokemonScore = otherPokemonBonuses.extraPokemonScore,
            herdScore = otherPokemonBonuses.herdScore,
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
        val targetEntity = (lookTarget as? EntityTracker)?.entity
        if (targetEntity === player || targetEntity?.id == player.id || targetEntity?.uuid == player.uuid) {
            return true
        }
        return lookTarget.currentPosition().distanceTo(player.getEyePosition(1.0F)) <= 1.0
    }

    private fun calculateOtherPokemonBonuses(
        primary: PokedexPhotoSubjectQuality,
        subjects: List<PokedexPhotoSubjectQuality>
    ): OtherPokemonBonuses {
        val secondarySubjects = subjects
            .asSequence()
            .filter { it !== primary }
            .toList()
        val extraPokemonScore = secondarySubjects.sumOf(::calculateSecondaryPokemonBonus)
            .coerceIn(0, Cobblemon.config.pokedexScannerPhotoExtraPokemonBonusCap.coerceAtLeast(0))
        val herdScore = secondarySubjects.sumOf { subject -> calculateFamilyBonus(primary, subject) }
            .coerceAtLeast(0)
        return OtherPokemonBonuses(extraPokemonScore, herdScore)
    }

    private fun calculateSecondaryPokemonBonus(subject: PokedexPhotoSubjectQuality): Int {
        if (subject.rayHits <= 0) return 0
        val visibility = (subject.rayCoverage / 0.08F).coerceIn(0.25F, 1F)
        val placement = (1F - subject.centerDistance).coerceIn(0.35F, 1F)
        val scoreCap = Cobblemon.config.pokedexScannerPhotoExtraPokemonBonusPerSubject.coerceAtLeast(0)
        return (scoreCap * visibility * placement).roundToInt().coerceIn(0, scoreCap)
    }

    private fun calculateFamilyBonus(primary: PokedexPhotoSubjectQuality, subject: PokedexPhotoSubjectQuality): Int {
        if (primary.species == subject.species) {
            return Cobblemon.config.pokedexScannerPhotoFamilyBonus.coerceAtLeast(0)
        }
        return if (areSpeciesInSameEvolutionLine(primary.species, subject.species)) {
            Cobblemon.config.pokedexScannerPhotoFamilyBonus.coerceAtLeast(0)
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

    private fun calculateAdditiveSubtotal(subject: PokedexPhotoSubjectQuality?): Int {
        if (subject == null) return 0
        return (subject.sizeScore +
                subject.placementScore +
                subject.otherPokemonScore +
                subject.directionScore +
                subject.herdScore +
                subject.wildScore +
                subject.formScore +
                subject.poseScore +
                subject.habitatScore +
                subject.timeOfDayScore +
                subject.weatherScore +
                subject.shinyScore +
                subject.alphaScore
            ).coerceIn(0, 5000)
    }

    private fun hasAlternateForm(pokemon: Pokemon): Boolean {
        return pokemon.form.name != pokemon.species.standardForm.name
    }

    private fun spawnDetailsForSpecies(species: ResourceLocation): List<PokemonSpawnDetail> {
        return CobblemonSpawnPools.WORLD_SPAWN_POOL
            .asSequence()
            .filterIsInstance<PokemonSpawnDetail>()
            .filter { detail -> detail.pokemon.species?.asIdentifierDefaultingNamespace() == species }
            .toList()
    }

    private fun detailAllowsBiomeAndDimension(
        detail: PokemonSpawnDetail,
        biome: ResourceLocation,
        dimension: ResourceLocation
    ): Boolean {
        if (biome !in detail.validBiomes) return false
        if (detail.conditions.isNotEmpty() && detail.conditions.none { condition ->
            condition.dimensions.isNullOrEmpty() || dimension in condition.dimensions!!
        }) {
            return false
        }
        if (detail.anticonditions.any { condition -> !condition.dimensions.isNullOrEmpty() && dimension in condition.dimensions!! }) {
            return false
        }
        return true
    }

    private fun detailAllowsTime(detail: PokemonSpawnDetail, timeOfDayTicks: Int): Boolean {
        if (detail.conditions.isNotEmpty() && detail.conditions.none { condition ->
            condition.timeRange == null || condition.timeRange!!.contains(timeOfDayTicks)
        }) {
            return false
        }
        if (detail.anticonditions.any { condition -> condition.timeRange?.contains(timeOfDayTicks) == true }) {
            return false
        }
        return true
    }

    private fun detailAllowsWeather(detail: PokemonSpawnDetail, raining: Boolean, thundering: Boolean): Boolean {
        if (detail.conditions.isNotEmpty() && detail.conditions.none { condition ->
            (condition.isRaining == null || condition.isRaining == raining) &&
                (condition.isThundering == null || condition.isThundering == thundering)
        }) {
            return false
        }
        if (detail.anticonditions.any { condition ->
            (condition.isRaining != null && condition.isRaining == raining) ||
                (condition.isThundering != null && condition.isThundering == thundering)
        }) {
            return false
        }
        return true
    }
}
