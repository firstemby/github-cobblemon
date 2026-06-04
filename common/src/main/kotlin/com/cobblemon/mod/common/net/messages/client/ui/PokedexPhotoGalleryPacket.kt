/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.ui

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.client.net.gui.PokedexPhotoGalleryPacketHandler
import com.cobblemon.mod.common.pokedex.photo.PokedexPhotoMetadata
import com.cobblemon.mod.common.pokedex.photo.PokedexPhotoSubjectQuality
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import java.util.UUID

/**
 * Sends a player's Pokédex photo listing to the client.
 *
 * Handled by [PokedexPhotoGalleryPacketHandler].
 */
class PokedexPhotoGalleryPacket(
    val maxPhotosAllowed: Int,
    val photos: List<PokedexPhotoMetadata>
) : NetworkPacket<PokedexPhotoGalleryPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeInt(maxPhotosAllowed)
        buffer.writeInt(photos.size)
        photos.forEach { photo ->
            buffer.writeUtf(photo.id)
            buffer.writeUUID(photo.owner)
            buffer.writeLong(photo.index)
            buffer.writeInt(photo.width)
            buffer.writeInt(photo.height)
            buffer.writeLong(photo.createdAt)
            buffer.writeInt(photo.dataSize)
            buffer.writeCollection(photo.detectedSpecies.orEmpty()) { _, species -> buffer.writeUtf(species) }
            buffer.writeUtf(photo.biome)
            buffer.writeUtf(photo.dimension)
            buffer.writeLong(photo.timeOfDayTicks)
            buffer.writeInt(photo.qualityScore)
            buffer.writeInt(photo.qualityBaseScore)
            buffer.writeFloat(photo.qualitySpeciesMultiplier)
            buffer.writeCollection(photo.qualitySubjects.orEmpty()) { _, subject ->
                buffer.writeUtf(subject.species)
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
                buffer.writeFloat(subject.animationMultiplier)
                buffer.writeFloat(subject.rarityMultiplier)
            }
        }
    }

    companion object {
        val ID = cobblemonResource("pokedex_photo_gallery")

        fun decode(buffer: RegistryFriendlyByteBuf): PokedexPhotoGalleryPacket {
            val maxAllowed = buffer.readInt()
            val photoCount = buffer.readInt()
            val photos = ArrayList<PokedexPhotoMetadata>(photoCount)
            repeat(photoCount) {
                photos += PokedexPhotoMetadata(
                    id = buffer.readUtf(),
                    owner = buffer.readUUID(),
                    index = buffer.readLong(),
                    width = buffer.readInt(),
                    height = buffer.readInt(),
                    createdAt = buffer.readLong(),
                    dataSize = buffer.readInt(),
                    detectedSpecies = buffer.readList { it.readUtf() },
                    biome = buffer.readUtf(),
                    dimension = buffer.readUtf(),
                    timeOfDayTicks = buffer.readLong(),
                    qualityScore = buffer.readInt(),
                    qualityBaseScore = buffer.readInt(),
                    qualitySpeciesMultiplier = buffer.readFloat(),
                    qualitySubjects = buffer.readList {
                        PokedexPhotoSubjectQuality(
                            species = it.readUtf(),
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
                            animationMultiplier = it.readFloat(),
                            rarityMultiplier = it.readFloat()
                        )
                    }
                )
            }
            return PokedexPhotoGalleryPacket(maxAllowed, photos)
        }
    }
}

