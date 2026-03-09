/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.pokemon

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMoLangValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.api.molang.ObjectValue
import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext
import com.cobblemon.mod.common.api.moves.animations.ActionEffectTimeline
import com.cobblemon.mod.common.api.moves.animations.ActionEffects
import com.cobblemon.mod.common.api.moves.animations.TargetsProvider
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
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

/**
 * The reference object for a skill that a Pokémon can use in the overworld.
 *
 * Most skills are "wrapper" skills that play a move's action effect at a target and run
 * some MoLang on impact. Custom skills can define a full action effect timeline.
 *
 * Access is auto-derived from grantingMoves/Abilities/Types/Species lists. If none are
 * populated, accessCondition is used as a fallback.
 *
 * @author Hiroku
 * @since August 31st, 2025
 */
class PokeSkill(
    val name: String,
    val enabled: Boolean = true,
    val actionEffect: ActionEffectTimeline,
    val icon: ResourceLocation,
    val cooldownTicks: Int,
    val accessCondition: ExpressionLike = "true".asExpressionLike(),
    val useCondition: ExpressionLike = "true".asExpressionLike(),
    // Targeting
    val targetType: TargetType? = null,
    val range: Double? = null,
    val targetCondition: ExpressionLike? = null,
    // Granting
    val grantingMoves: List<String>? = null,
    val grantingAbilities: List<String>? = null,
    val grantingTypes: List<String>? = null,
    val grantingSpecies: List<String>? = null,
    // Wrapper skill fields
    val hitDelay: ExpressionLike? = null,
    val hitEffect: ResourceLocation? = null,
    val fallbackActionEffect: ResourceLocation? = null
) {
    // Enum to determine what this skill will target.
    enum class TargetType {
        NONE, SELF, BLOCK, ENTITY, BOTH
    }

    companion object {
        val runtime = MoLangRuntime().setup()
        val CODEC = RecordCodecBuilder.create<PokeSkill> { instance ->
            instance.group(
                Codec.STRING.fieldOf("name").forGetter { it.name },
                Codec.BOOL.fieldOf("enabled").forGetter { it.enabled },
                Codec.unit<ActionEffectTimeline> { ActionEffectTimeline.NONE }.fieldOf("actionEffect").forGetter { it.actionEffect },
                ResourceLocation.CODEC.fieldOf("icon").forGetter { it.icon },
                Codec.INT.fieldOf("cooldownTicks").forGetter { it.cooldownTicks },
                EXPRESSION_LIKE_CODEC.fieldOf("accessCondition").forGetter { it.accessCondition },
                EXPRESSION_LIKE_CODEC.fieldOf("useCondition").forGetter { it.useCondition }
            ).apply(instance, ::PokeSkill)
        }
        val PACKET_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC)
        private val DEFAULT_ACTION_EFFECT = cobblemonResource("default_poke_skill")
    }

    @Transient
    lateinit var id: ResourceLocation


//    fun canAccess(player: Player, pokemon: Pokemon): Boolean {
//        if (!enabled) {
//            return false
//        }
//
//        runtime.withQueryValue("player", player.asMostSpecificMoLangValue())
//        runtime.withQueryValue("pokemon", pokemon.struct)
//        pokemon.entity?.let { runtime.withQueryValue("entity", it.asMostSpecificMoLangValue()) }
//        val hasAccess = runtime.resolveBoolean(accessCondition)
//        return hasAccess
//    }

    /**
     * Determines access by checking the pokemons data against the granting lists.
     * If no granting lists are populated, accessCondition is used as a fallback.
     */
    fun canAccess(player: Player, pokemon: Pokemon): Boolean {
        if (!enabled) {
            return false
        }

        // Check granting lists — if any are populated, check if the player has any of the listed moves/abilities/types/species
        val hasGrantingLists = !grantingMoves.isNullOrEmpty() || !grantingAbilities.isNullOrEmpty() ||
                !grantingTypes.isNullOrEmpty() || !grantingSpecies.isNullOrEmpty()

        if (hasGrantingLists) {
            val moveMatch = !grantingMoves.isNullOrEmpty() && grantingMoves.any { moveName ->
                pokemon.moveSet.any { it.template.name == moveName }
            }
            val abilityMatch = !grantingAbilities.isNullOrEmpty() && grantingAbilities.any { abilityName ->
                pokemon.ability.name == abilityName
            }
            val typeMatch = !grantingTypes.isNullOrEmpty() && grantingTypes.any { typeName ->
                pokemon.types.any { it.name.equals(typeName, ignoreCase = true) }
            }
            val speciesMatch = !grantingSpecies.isNullOrEmpty() && grantingSpecies.any { speciesName ->
                pokemon.species.name.equals(speciesName, ignoreCase = true)
            }

            if (!(moveMatch || abilityMatch || typeMatch || speciesMatch)) return false
        }

        // Always evaluate accessCondition — acts as additional gate when granting lists are present,
        // or as the sole gate when no granting lists are populated
        runtime.withQueryValue("player", player.asMostSpecificMoLangValue())
        runtime.withQueryValue("pokemon", pokemon.struct)
        pokemon.entity?.let { runtime.withQueryValue("entity", it.asMostSpecificMoLangValue()) }
        return runtime.resolveBoolean(accessCondition ?: "true".asExpressionLike())
    }

    fun canUse(player: Player, pokemon: Pokemon): Boolean {
        val status = pokemon.status?.status
        if (id in pokemon.skillCooldownsTicks || player.isInBattle() || status == Statuses.SLEEP || status == Statuses.FROZEN) {
            return false
        }
        runtime.withQueryValue("player", player.asMostSpecificMoLangValue())
        runtime.withQueryValue("pokemon", pokemon.struct)
        val canUse = runtime.resolveBoolean(useCondition)
        return canUse
    }

    /**
     * Resolves which move from the pokemon's moveset grants this skill.
     * Returns the first matching move name from grantingMoves, or null if none match.
     */
    fun resolveGrantingMove(pokemon: Pokemon): String? {
        if (grantingMoves.isNullOrEmpty()) return null  // need this guard
        return grantingMoves.firstOrNull { moveName ->
            pokemon.moveSet.any { it.template.name == moveName }
        }
    }

    /**
     * Resolves the action effect to use for the move visual.
     * Priority: granting move's action effect > fallbackActionEffect > null
     */
    fun resolveVisualEffect(pokemon: Pokemon): ResourceLocation? {
        val grantingMove = resolveGrantingMove(pokemon)
        if (grantingMove != null) {
            val effectId = cobblemonResource(grantingMove)
            if (ActionEffects.actionEffects.containsKey(effectId)) return effectId
            Cobblemon.LOGGER.warn("PokeSkill '$id': granting move '$grantingMove' has no action effect at '$effectId'")
        }
        return fallbackActionEffect
    }

    /**
     * Runs the skill on the given pokemon.
     * Populates context and runs targeting logic.
     */
    fun use(
        player: ServerPlayer,
        pokemon: Pokemon,
        // context information!!!
    ): Boolean {
        if (!canUse(player, pokemon)) return false
        val entity = pokemon.entity ?: return false
        val level = player.level() as? ServerLevel ?: return false
        // I think these should be defaulted but making them nullable means I don't have to adjust the
        // other jsons that don't populate them yet...
        val effectiveTargetType = targetType ?: TargetType.NONE
        val effectiveRange = range ?: 8.0

        // Resolve the action effect to run
        val effectTimeline = if (actionEffect != null && actionEffect != ActionEffectTimeline.NONE) {
            actionEffect
        } else {
            ActionEffects.actionEffects[DEFAULT_ACTION_EFFECT] ?: run {
                Cobblemon.LOGGER.warn("PokeSkill '$id': default_poke_skill action effect not found")
                return false
            }
        }

        // Set up runtime
        val skillRuntime = MoLangRuntime().setup()
        skillRuntime.withQueryValue("player", player.asMostSpecificMoLangValue())
        skillRuntime.withQueryValue("pokemon", pokemon.struct)
        skillRuntime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        skillRuntime.environment.setSimpleVariable("skill_id", StringValue(id.toString()))
        skillRuntime.environment.setSimpleVariable("target_type", StringValue(effectiveTargetType.name.lowercase()))


        // Targeting — raycast based on targetType
        when (effectiveTargetType) {
            TargetType.SELF -> {
                injectEntityTarget(skillRuntime, player)
                skillRuntime.environment.setSimpleVariable("target_is_entity", DoubleValue(1.0))
            }
            TargetType.BLOCK -> {
                val blockHit = getPlayerLookedBlock(player, effectiveRange)
                if (blockHit == null) return false
                injectBlockTarget(skillRuntime, blockHit, level)
                skillRuntime.environment.setSimpleVariable("target_is_entity", DoubleValue(0.0))
            }
            TargetType.ENTITY -> {
                val entityHit = getPlayerLookedEntity(player, effectiveRange)
                if (entityHit == null) return false
                injectEntityTarget(skillRuntime, entityHit)
                skillRuntime.environment.setSimpleVariable("target_is_entity", DoubleValue(1.0))
            }
            TargetType.BOTH -> {
                val entityHit = getPlayerLookedEntity(player, effectiveRange)
                if (entityHit != null) {
                    injectEntityTarget(skillRuntime, entityHit)
                    skillRuntime.environment.setSimpleVariable("target_is_entity", DoubleValue(1.0))
                } else {
                    val blockHit = getPlayerLookedBlock(player, effectiveRange)
                    if (blockHit == null) return false
                    injectBlockTarget(skillRuntime, blockHit, level)
                    skillRuntime.environment.setSimpleVariable("target_is_entity", DoubleValue(0.0))
                }
            }
            TargetType.NONE -> {}
        }

        // Evaluate target condition
        if (targetCondition != null && !skillRuntime.resolveBoolean(targetCondition)) return false

        // Inject wrapper skill variables
        val visualEffect = resolveVisualEffect(pokemon)
        if (visualEffect != null) {
            skillRuntime.environment.setSimpleVariable("granting_move", StringValue(visualEffect.toString()))
        }
        if (hitEffect != null) {
            skillRuntime.environment.setSimpleVariable("hit_effect", StringValue(hitEffect.toString()))
        }
        if (hitDelay != null) {
            skillRuntime.environment.setSimpleVariable("hit_delay", hitDelay.resolve(skillRuntime))
        }


        // Build providers
        val providers = mutableListOf<Any>(
            UsersProvider(entity)
        )

        // Build and run context
        val context = ActionEffectContext(
            actionEffect = effectTimeline,
            runtime = skillRuntime,
            providers = providers,
            level = level
        )

        effectTimeline.run(context).thenRun {
            // Auto-apply cooldown after effect completes
            pokemon.skillCooldownsTicks[id] = cooldownTicks
        }

        return true
    }

    /**
     * Utility functions for setting up the runtime for a skill.
     */
    private fun injectBlockTarget(runtime: MoLangRuntime, blockHit: BlockHitResult, level: ServerLevel) {
        val pos = blockHit.blockPos
        runtime.environment.setSimpleVariable("target_x", DoubleValue(pos.x + 0.5))
        runtime.environment.setSimpleVariable("target_y", DoubleValue(pos.y + 0.5))
        runtime.environment.setSimpleVariable("target_z", DoubleValue(pos.z + 0.5))
        runtime.environment.setSimpleVariable("target_block_x", DoubleValue(pos.x.toDouble()))
        runtime.environment.setSimpleVariable("target_block_y", DoubleValue(pos.y.toDouble()))
        runtime.environment.setSimpleVariable("target_block_z", DoubleValue(pos.z.toDouble()))

        val blockState = level.getBlockState(pos)
        val blockHolder = level.blockRegistry.wrapAsHolder(blockState.block)
        val blockValue = blockHolder.asMoLangValue(Registries.BLOCK)

        blockValue.addFunction("x") { DoubleValue(pos.x.toDouble()) }
        blockValue.addFunction("y") { DoubleValue(pos.y.toDouble()) }
        blockValue.addFunction("z") { DoubleValue(pos.z.toDouble()) }

        runtime.environment.setSimpleVariable("target_block", blockValue)
    }

    private fun injectEntityTarget(runtime: MoLangRuntime, entityHit: Entity) {
        runtime.environment.setSimpleVariable("target_x", DoubleValue(entityHit.x))
        runtime.environment.setSimpleVariable("target_y", DoubleValue(entityHit.y + entityHit.bbHeight / 2.0))
        runtime.environment.setSimpleVariable("target_z", DoubleValue(entityHit.z))
        runtime.environment.setSimpleVariable("target_entity", entityHit.asMostSpecificMoLangValue())
    }

    /**
     * simple raycast to get the block the player is looking at.
     */
    private fun getPlayerLookedBlock(player: ServerPlayer, range: Double): BlockHitResult? {
        val hitResult = player.pick(range, 0f, true)
        return if (hitResult.type == HitResult.Type.BLOCK) hitResult as BlockHitResult else null
    }

    /**
     * Raycast to determine the entity the player is looking at
     */
    private fun getPlayerLookedEntity(player: ServerPlayer, range: Double): Entity? {
        val eyePosition = player.getEyePosition(1.0f)
        val lookVector = player.getViewVector(1.0f)
        val reachVector = eyePosition.add(lookVector.x * range, lookVector.y * range, lookVector.z * range)

        // Create an outer bounding box to find potential candidates
        val searchBox = player.boundingBox.expandTowards(lookVector.scale(range)).inflate(1.0, 1.0, 1.0)

        // Filter entities within the search box
        val entityHitResult = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
            player,
            eyePosition,
            reachVector,
            searchBox,
            { entity -> !entity.isSpectator && entity.isPickable },
            range * range
        )

        return entityHitResult?.entity
    }
}