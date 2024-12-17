/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokemon.breeding

import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.block.entity.NestBlockEntity
import com.cobblemon.mod.common.util.DataKeys
import com.cobblemon.mod.common.util.codec.CodecUtils
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import java.util.*

data class Egg(
        val species: ResourceLocation, // Species of the Pokémon
        val parentOneUUID: UUID,       // First parent's UUID
        val parentTwoUUID: UUID,       // Second parent's UUID
        var timeToHatch: Int           // Time in ticks to hatch
) {

    companion object {
        val CODEC: Codec<Egg> = RecordCodecBuilder.create { instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("species").forGetter { it.species },
                    CodecUtils.UUID_CODEC.fieldOf("parentOneUUID").forGetter { it.parentOneUUID },
                    CodecUtils.UUID_CODEC.fieldOf("parentTwoUUID").forGetter { it.parentTwoUUID },
                    Codec.INT.fieldOf("timeToHatch").forGetter { it.timeToHatch }
            ).apply(instance, ::Egg)
        }

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, Egg> = StreamCodec.of(
                { buf, egg -> // Write Egg to buffer
                    buf.writeResourceLocation(egg.species)
                    buf.writeUUID(egg.parentOneUUID)
                    buf.writeUUID(egg.parentTwoUUID)
                    buf.writeInt(egg.timeToHatch)
                },
                { buf -> // Read Egg from buffer
                    Egg(
                            species = buf.readResourceLocation(),
                            parentOneUUID = buf.readUUID(),
                            parentTwoUUID = buf.readUUID(),
                            timeToHatch = buf.readInt()
                    )
                }
        )
    }



    fun asItemStack(blockEntityNbt: CompoundTag): ItemStack {
        val stack = CobblemonBlocks.EGG.asItem().defaultInstance
        //FIXME: Use properties
        //BlockItem.setBlockEntityNbt(stack, CobblemonBlockEntities.EGG, blockEntityNbt)
        return stack
    }
}

    /*fun getPattern(): EggPattern? {
        return EggPatterns.patternMap[patternId]
    }

    companion object {
        fun fromBlockNbt(blockNbt: CompoundTag): Egg {
            val eggNbt = blockNbt.get(DataKeys.EGG) as CompoundTag
            return fromNbt(eggNbt)
        }
        fun fromNbt(nbt: CompoundTag): Egg {
            return Egg(
                EggPokemon.fromNBT(nbt.get(DataKeys.HATCHED_POKEMON) as CompoundTag),
                ResourceLocation.tryParse(nbt.getString(DataKeys.EGG_PATTERN))!!,
                nbt.getString(DataKeys.PRIMARY_COLOR),
                if (nbt.contains(DataKeys.SECONDARY_COLOR)) nbt.getString(DataKeys.SECONDARY_COLOR) else null,
                nbt.getInt(DataKeys.TIME_TO_HATCH)
            )
        }

        //Less expensive than deserializing the whole thing, used for the color provider since it gets
        //called every frame
        fun getColorsFromNbt(nbt: CompoundTag): Pair<String, String> {
            return Pair(
                nbt.getString(DataKeys.PRIMARY_COLOR),
                nbt.getString(DataKeys.SECONDARY_COLOR)
            )
        }
    }
}*/