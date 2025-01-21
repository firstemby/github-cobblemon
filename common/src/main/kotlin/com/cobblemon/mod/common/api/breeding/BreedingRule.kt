package com.cobblemon.mod.common.api.breeding

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.resources.ResourceLocation
import java.util.Optional

data class BreedingRule(
        val parent1rules: ParentRules,
        val parent2rules: ParentRules,

        // The resulting baby data (all strings except hasItem will appear in ParentRules)
        val species: String?,
        val form: String?,
        val nature: String?,
        val ability: String?,

        val moves: Moves,
        val ivs: Ivs,
        val gender: String?,

        // If you want a separate pokeball string field:
        val pokeball: String?
) {
    companion object {
        val CODEC: Codec<BreedingRule> = RecordCodecBuilder.create { builder ->
            builder.group(
                    // Parent rules
                    ParentRules.CODEC.fieldOf("parent1rules")
                            .forGetter<BreedingRule> { it.parent1rules },

                    ParentRules.CODEC.fieldOf("parent2rules")
                            .forGetter<BreedingRule> { it.parent2rules },

                    // ---- All optional string fields for the baby data ----
                    Codec.STRING.optionalFieldOf("species")
                            .xmap({ it.orElse(null) }, { Optional.ofNullable(it) })
                            .forGetter<BreedingRule> { it.species },

                    Codec.STRING.optionalFieldOf("form")
                            .xmap({ it.orElse(null) }, { Optional.ofNullable(it) })
                            .forGetter<BreedingRule> { it.form },

                    Codec.STRING.optionalFieldOf("nature")
                            .xmap({ it.orElse(null) }, { Optional.ofNullable(it) })
                            .forGetter<BreedingRule> { it.nature },

                    Codec.STRING.optionalFieldOf("ability")
                            .xmap({ it.orElse(null) }, { Optional.ofNullable(it) })
                            .forGetter<BreedingRule> { it.ability },

                    // Moves & IVs can be empty objects but must exist in the JSON
                    Moves.CODEC.fieldOf("moves")
                            .forGetter<BreedingRule> { it.moves },

                    Ivs.CODEC.fieldOf("ivs")
                            .forGetter<BreedingRule> { it.ivs },

                    Codec.STRING.optionalFieldOf("gender")
                            .xmap({ it.orElse(null) }, { Optional.ofNullable(it) })
                            .forGetter<BreedingRule> { it.gender },

                    // If you want pokeball to be a string instead of ResourceLocation
                    Codec.STRING.optionalFieldOf("pokeball")
                            .xmap({ it.orElse(null) }, { Optional.ofNullable(it) })
                            .forGetter<BreedingRule> { it.pokeball }

            ).apply(builder, ::BreedingRule)
        }
    }

    data class ParentRules(
            val species: String?,
            val form: String?,
            val ability: String?,
            val gender: String?,

            // hasMove is also just a string now
            val hasMove: String?,

            // hasItem stays as a ResourceLocation?
            val hasItem: ResourceLocation?
    ) {
        companion object {
            val CODEC: Codec<ParentRules> = RecordCodecBuilder.create { builder ->
                builder.group(
                        Codec.STRING.optionalFieldOf("species")
                                .xmap({ it.orElse(null) }, { Optional.ofNullable(it) })
                                .forGetter<ParentRules> { it.species },

                        Codec.STRING.optionalFieldOf("form")
                                .xmap({ it.orElse(null) }, { Optional.ofNullable(it) })
                                .forGetter<ParentRules> { it.form },

                        Codec.STRING.optionalFieldOf("ability")
                                .xmap({ it.orElse(null) }, { Optional.ofNullable(it) })
                                .forGetter<ParentRules> { it.ability },

                        Codec.STRING.optionalFieldOf("gender")
                                .xmap({ it.orElse(null) }, { Optional.ofNullable(it) })
                                .forGetter<ParentRules> { it.gender },

                        Codec.STRING.optionalFieldOf("hasMove")
                                .xmap({ it.orElse(null) }, { Optional.ofNullable(it) })
                                .forGetter<ParentRules> { it.hasMove },

                        // Keep hasItem as a ResourceLocation
                        ResourceLocation.CODEC.optionalFieldOf("hasItem")
                                .xmap({ it.orElse(null) }, { Optional.ofNullable(it) })
                                .forGetter<ParentRules> { it.hasItem }

                ).apply(builder, ::ParentRules)
            }
        }
    }

    data class Moves(
            val move1: String?,
            val move2: String?,
            val move3: String?,
            val move4: String?
    ) {
        companion object {
            val CODEC: Codec<Moves> = RecordCodecBuilder.create { builder ->
                builder.group(
                        Codec.STRING.optionalFieldOf("move1")
                                .xmap({ it.orElse(null) }, { Optional.ofNullable(it) })
                                .forGetter<Moves> { it.move1 },

                        Codec.STRING.optionalFieldOf("move2")
                                .xmap({ it.orElse(null) }, { Optional.ofNullable(it) })
                                .forGetter<Moves> { it.move2 },

                        Codec.STRING.optionalFieldOf("move3")
                                .xmap({ it.orElse(null) }, { Optional.ofNullable(it) })
                                .forGetter<Moves> { it.move3 },

                        Codec.STRING.optionalFieldOf("move4")
                                .xmap({ it.orElse(null) }, { Optional.ofNullable(it) })
                                .forGetter<Moves> { it.move4 }

                ).apply(builder, ::Moves)
            }
        }
    }

    data class Ivs(
            val hp: Int?,
            val attack: Int?,
            val defence: Int?,
            val special_attack: Int?,
            val special_defence: Int?,
            val speed: Int?
    ) {
        companion object {
            val CODEC: Codec<Ivs> = RecordCodecBuilder.create { builder ->
                builder.group(
                        Codec.INT.optionalFieldOf("hp")
                                .xmap({ it.orElse(null) }, { Optional.ofNullable(it) })
                                .forGetter<Ivs> { it.hp },

                        Codec.INT.optionalFieldOf("attack")
                                .xmap({ it.orElse(null) }, { Optional.ofNullable(it) })
                                .forGetter<Ivs> { it.attack },

                        Codec.INT.optionalFieldOf("defence")
                                .xmap({ it.orElse(null) }, { Optional.ofNullable(it) })
                                .forGetter<Ivs> { it.defence },

                        Codec.INT.optionalFieldOf("special_attack")
                                .xmap({ it.orElse(null) }, { Optional.ofNullable(it) })
                                .forGetter<Ivs> { it.special_attack },

                        Codec.INT.optionalFieldOf("special_defence")
                                .xmap({ it.orElse(null) }, { Optional.ofNullable(it) })
                                .forGetter<Ivs> { it.special_defence },

                        Codec.INT.optionalFieldOf("speed")
                                .xmap({ it.orElse(null) }, { Optional.ofNullable(it) })
                                .forGetter<Ivs> { it.speed }
                ).apply(builder, ::Ivs)
            }
        }
    }
}
