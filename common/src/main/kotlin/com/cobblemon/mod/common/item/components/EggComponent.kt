/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.item.components

import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import java.util.UUID
import net.minecraft.core.UUIDUtil
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation

/**
 * A component that stores the visible data about an egg.
 */
class EggComponent(
    var identifier: ResourceLocation,
    var variant: String,
    var eggId: UUID,
    var parent1UUID: UUID?,
    var parent1OwnerUUID: UUID?,
    var parent2UUID: UUID?,
    var parent2OwnerUUID: UUID?,
    var ticksToHatch: Float // Float because some things that reduce the remaining time are fractional
) {
    constructor(
        offspring: Pokemon,
        parent1: Pokemon?,
        parent2: Pokemon?,
        ticksToHatch: Float
    ): this(
        identifier = "${offspring.species.resourceIdentifier}_egg".asIdentifierDefaultingNamespace(),
        variant = offspring.form.name.lowercase().replace("[^a-z0-9]".toRegex(), ""),
        eggId = offspring.uuid,
        parent1UUID = parent1?.uuid,
        parent1OwnerUUID = parent1?.getOwnerUUID(),
        parent2UUID = parent2?.uuid,
        parent2OwnerUUID = parent2?.getOwnerUUID(),
        ticksToHatch = ticksToHatch
    )

    val struct = ObjectValue(this).also {
        it.addFunction("identifier") { StringValue(identifier.toString()) }
        it.addFunction("variant") { StringValue(variant) }
        it.addFunction("egg_id") { StringValue(eggId.toString()) }
        it.addFunction("is_wild") { DoubleValue(isWild) }
        it.addFunction("parent_1_uuid") { parent1UUID?.let { StringValue(it.toString()) } ?: DoubleValue.ZERO }
        it.addFunction("parent_2_uuid") { parent2UUID?.let { StringValue(it.toString()) } ?: DoubleValue.ZERO }
        it.addFunction("parent_1_owner_uuid") { parent1OwnerUUID?.let { StringValue(it.toString()) } ?: DoubleValue.ZERO }
        it.addFunction("parent_2_owner_uuid") { parent2OwnerUUID?.let { StringValue(it.toString()) } ?: DoubleValue.ZERO }
        it.addFunction("ticks_to_hatch") { DoubleValue(ticksToHatch) }
        it.addFunction("set_identifier") { params -> identifier = params.getString(0).asIdentifierDefaultingNamespace() }
        it.addFunction("set_variant") { params -> variant = params.getString(0).lowercase().replace("[^a-z0-9]".toRegex(), "") }
        it.addFunction("set_ticks_to_hatch") { params -> ticksToHatch = params.getDouble(0).toFloat() }
    }

    companion object {
        // Codec for saving/loading the component (serialization)
        val CODEC: Codec<EggComponent> = RecordCodecBuilder.create { instance ->
            instance.group(
                ResourceLocation.CODEC.fieldOf("identifier").forGetter(EggComponent::identifier),
                Codec.STRING.fieldOf("variant").forGetter(EggComponent::variant),
                UUIDUtil.LENIENT_CODEC.fieldOf("eggId").forGetter(EggComponent::eggId),
                UUIDUtil.LENIENT_CODEC.optionalFieldOf("parent1UUID", null).forGetter { it?.parent1UUID },
                UUIDUtil.LENIENT_CODEC.optionalFieldOf("parent1OwnerUUID", null).forGetter { it?.parent1OwnerUUID },
                UUIDUtil.LENIENT_CODEC.optionalFieldOf("parent2UUID", null).forGetter { it?.parent2UUID },
                UUIDUtil.LENIENT_CODEC.optionalFieldOf("parent2OwnerUUID", null).forGetter { it?.parent2OwnerUUID },
                Codec.FLOAT.optionalFieldOf("ticksToHatch", 0F).forGetter { it?.ticksToHatch ?: 0F }
            ).apply(instance, ::EggComponent)
        }

        // StreamCodec for network synchronization
        val PACKET_CODEC: StreamCodec<ByteBuf, EggComponent> = ByteBufCodecs.fromCodec(CODEC)
    }

    val isWild = parent1OwnerUUID == null && parent2OwnerUUID == null
    fun isParent(pokemonId: UUID) = parent1OwnerUUID == pokemonId || parent2OwnerUUID == pokemonId

    override fun equals(other: Any?) = other is EggComponent && this.eggId == other.eggId
    override fun hashCode() = eggId.hashCode()
}
