/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.ui

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.client.net.gui.PokedexPhotoUploadPacketHandler
import com.cobblemon.mod.common.pokedex.photo.PokedexPhotoSubjectQuality
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * Uploads a captured Pokédex scan photo to the server.
 *
 * Handled by [PokedexPhotoUploadPacketHandler].
 */
class PokedexPhotoUploadPacket(
    val width: Int,
    val height: Int,
    val detectedSpecies: List<String>,
    val biome: String,
    val dimension: String,
    val timeOfDayTicks: Long,
    val qualityScore: Int,
    val qualitySubjects: List<PokedexPhotoSubjectQuality>,
    val pngData: ByteArray
) : NetworkPacket<PokedexPhotoUploadPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeInt(width)
        buffer.writeInt(height)
        buffer.writeCollection(detectedSpecies.orEmpty()) { _, species -> buffer.writeUtf(species) }
        buffer.writeUtf(biome)
        buffer.writeUtf(dimension)
        buffer.writeLong(timeOfDayTicks)
        buffer.writeInt(qualityScore)
        buffer.writeCollection(qualitySubjects.orEmpty()) { _, subject ->
            buffer.writeUtf(subject.species)
            buffer.writeBoolean(subject.entityUuid != null)
            subject.entityUuid?.let(buffer::writeUUID)
            buffer.writeBoolean(subject.primarySubject)
            buffer.writeInt(subject.subjectScore)
            buffer.writeInt(subject.sizeScore)
            buffer.writeInt(subject.placementScore)
            buffer.writeFloat(subject.centerDistance)
            buffer.writeInt(subject.distanceScore)
            buffer.writeInt(subject.directionScore)
            buffer.writeInt(subject.poseScore)
            buffer.writeInt(subject.fullBodyScore)
            buffer.writeInt(subject.otherPokemonScore)
            buffer.writeInt(subject.rayHits)
            buffer.writeFloat(subject.rayCoverage)
            buffer.writeInt(subject.perimeterHits)
            buffer.writeBoolean(subject.wholeBodyInFrame)
            buffer.writeFloat(subject.clippingMultiplier)
            buffer.writeCollection(subject.activeAnimations.orEmpty()) { _, animation -> buffer.writeUtf(animation) }
            buffer.writeBoolean(subject.shiny)
            buffer.writeFloat(subject.shinyMultiplier)
            buffer.writeFloat(subject.rarityMultiplier)
        }
        buffer.writeByteArray(pngData)
    }

    companion object {
        val ID = cobblemonResource("pokedex_photo_upload")

        fun decode(buffer: RegistryFriendlyByteBuf): PokedexPhotoUploadPacket {
            return PokedexPhotoUploadPacket(
                width = buffer.readInt(),
                height = buffer.readInt(),
                detectedSpecies = buffer.readList { it.readUtf() },
                biome = buffer.readUtf(),
                dimension = buffer.readUtf(),
                timeOfDayTicks = buffer.readLong(),
                qualityScore = buffer.readInt(),
                qualitySubjects = buffer.readList {
                    PokedexPhotoSubjectQuality(
                        species = it.readUtf(),
                        entityUuid = if (it.readBoolean()) it.readUUID() else null,
                        primarySubject = it.readBoolean(),
                        subjectScore = it.readInt(),
                        sizeScore = it.readInt(),
                        placementScore = it.readInt(),
                        centerDistance = it.readFloat(),
                        distanceScore = it.readInt(),
                        directionScore = it.readInt(),
                        poseScore = it.readInt(),
                        fullBodyScore = it.readInt(),
                        otherPokemonScore = it.readInt(),
                        rayHits = it.readInt(),
                        rayCoverage = it.readFloat(),
                        perimeterHits = it.readInt(),
                        wholeBodyInFrame = it.readBoolean(),
                        clippingMultiplier = it.readFloat(),
                        activeAnimations = it.readList { buffer -> buffer.readUtf() },
                        shiny = it.readBoolean(),
                        shinyMultiplier = it.readFloat(),
                        rarityMultiplier = it.readFloat()
                    )
                },
                pngData = buffer.readByteArray()
            )
        }
    }
}

