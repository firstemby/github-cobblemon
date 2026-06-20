package com.cobblemon.mod.common.entity.pokemon.pokeskills

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMoLangValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext
import com.cobblemon.mod.common.api.moves.animations.ActionEffectTimeline
import com.cobblemon.mod.common.api.moves.animations.ActionEffects
import com.cobblemon.mod.common.api.moves.animations.UsersProvider
import com.cobblemon.mod.common.api.pokemon.status.Statuses
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.blockRegistry
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.codec.EXPRESSION_LIKE_CODEC
import com.cobblemon.mod.common.util.isInBattle
import com.cobblemon.mod.common.util.resolveBoolean
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.registries.Registries
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

/**
 * The reference object for a skill that a Pokémon can use in the overworld.
 *
 * Most skills are "wrapper" skills that play a move's action effect at a target and run
 * some MoLang on impact. Custom skills can define a full action effect timeline.
 *
 * Access is auto-derived from requiredMoves/Abilities/Types/Species lists. If none are
 * populated, accessCondition is used as a fallback.
 *
 * @author Hiroku
 * @since August 31st, 2025
 */
class PokeSkill(
    val name: String,
    val enabled: Boolean? = null,
    val actionEffect: ActionEffectTimeline? = null,
    val icon: ResourceLocation? = null,
    val cooldownTicks: Int? = null,
    val accessCondition: ExpressionLike? = null,
    val useCondition: ExpressionLike? = null,

    val targetType: PokeSkillTargetType? = null,
    val range: Double? = null,
    val targetCondition: ExpressionLike? = null,

    val requiredMoves: List<String>? = null,
    val requiredAbilities: List<String>? = null,
    val requiredTypes: List<String>? = null,
    val requiredSpecies: List<String>? = null,

    val hitDelay: ExpressionLike? = null,
    val hitEffect: ResourceLocation? = null,
    val fallbackActionEffect: ResourceLocation? = null,

    val variables: Map<String, Any>? = null,

    val tiers: List<PokeSkillTier>,
) {
    companion object {
        val runtime = MoLangRuntime().setup()
        // TODO add all fields to codec
        val CODEC = RecordCodecBuilder.create<PokeSkill> { instance ->
            instance.group(
                Codec.STRING.fieldOf("name").forGetter { it.name },
                Codec.BOOL.fieldOf("enabled").forGetter { it.enabled },
                Codec.unit<ActionEffectTimeline> { ActionEffectTimeline.NONE }.fieldOf("actionEffect").forGetter { it.actionEffect },
                ResourceLocation.CODEC.fieldOf("icon").forGetter { it.icon },
                Codec.INT.fieldOf("cooldownTicks").forGetter { it.cooldownTicks },
                EXPRESSION_LIKE_CODEC.fieldOf("accessCondition").forGetter { it.accessCondition },
                EXPRESSION_LIKE_CODEC.fieldOf("useCondition").forGetter { it.useCondition }
            ).apply(instance) { name, enabled, actionEffect, icon, cooldownTicks, accessCondition, useCondition ->
                PokeSkill(
                    name = name,
                    enabled = enabled,
                    actionEffect = actionEffect,
                    icon = icon,
                    cooldownTicks = cooldownTicks,
                    accessCondition = accessCondition,
                    useCondition = useCondition,
                    targetType = null,
                    range = null,
                    targetCondition = null,
                    requiredMoves = null,
                    requiredAbilities = null,
                    requiredTypes = null,
                    requiredSpecies = null,
                    hitDelay = null,
                    hitEffect = null,
                    fallbackActionEffect = null,
                    tiers = emptyList()
                )
            }
        }

        val PACKET_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC)
    }

    @Transient
    lateinit var id: ResourceLocation
}