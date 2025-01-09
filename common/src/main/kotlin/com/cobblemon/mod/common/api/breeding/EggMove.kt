package com.cobblemon.mod.common.api.breeding

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.resources.ResourceLocation
import java.util.*

data class EggMove(
        val baby: ResourceLocation,
        val parent1rules: ParentRules,
        val parent2rules: ParentRules,
        val eggMove: ResourceLocation
) {
    companion object {
        val CODEC: Codec<EggMove> = RecordCodecBuilder.create { builder ->
            builder.group(
                    ResourceLocation.CODEC.fieldOf("baby").forGetter<EggMove> { it.baby },
                    ParentRules.CODEC.fieldOf("parent1rules").forGetter<EggMove> { it.parent1rules },
                    ParentRules.CODEC.fieldOf("parent2rules").forGetter<EggMove> { it.parent2rules },
                    ResourceLocation.CODEC.fieldOf("eggMove").forGetter<EggMove> { it.eggMove }
            ).apply(builder, ::EggMove)
        }
    }

    data class ParentRules(
            val species: ResourceLocation?,
            val gender: String?,
            val hasMove: ResourceLocation?,
            val hasItem: ResourceLocation?
    ) {
        companion object {
            val CODEC: Codec<ParentRules> = RecordCodecBuilder.create { builder ->
                builder.group(
                        ResourceLocation.CODEC.optionalFieldOf("species")
                                .xmap({ it.orElse(null) }, { Optional.ofNullable(it) })
                                .forGetter<ParentRules> { it.species },
                        Codec.STRING.optionalFieldOf("gender") // Use optionalFieldOf for nullable fields
                                .xmap({ it.orElse(null) }, { Optional.ofNullable(it) })
                                .forGetter<ParentRules> { it.gender },
                        ResourceLocation.CODEC.optionalFieldOf("hasMove")
                                .xmap({ it.orElse(null) }, { Optional.ofNullable(it) })
                                .forGetter<ParentRules> { it.hasMove },
                        ResourceLocation.CODEC.optionalFieldOf("hasItem")
                                .xmap({ it.orElse(null) }, { Optional.ofNullable(it) })
                                .forGetter<ParentRules> { it.hasItem }
                ).apply(builder, ::ParentRules)
            }
        }
    }
}


