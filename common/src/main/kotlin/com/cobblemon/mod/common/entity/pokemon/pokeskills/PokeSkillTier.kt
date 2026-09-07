package com.cobblemon.mod.common.entity.pokemon.pokeskills

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.MoValue
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
import com.cobblemon.mod.common.entity.pokemon.pokeskills.PokeSkill.Companion.runtime
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.blockRegistry
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.isInBattle
import com.cobblemon.mod.common.util.resolveBoolean
import com.cobblemon.mod.common.util.withQueryValue
import com.google.gson.annotations.SerializedName
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import kotlin.collections.set

class PokeSkillTier(
    @SerializedName("enabled")
    private val _enabled: Boolean? = null,
    @SerializedName("actionEffect")
    private val _actionEffect: ActionEffectTimeline? = null,
    @SerializedName("icon")
    private val _icon: ResourceLocation? = null,
    @SerializedName("cooldownTicks")
    private val _cooldownTicks: Int? = null,
    @SerializedName("accessCondition")
    private val _accessCondition: ExpressionLike? = null,
    @SerializedName("useCondition")
    private val _useCondition: ExpressionLike? = null,

    @SerializedName("targetType")
    private val _targetType: PokeSkillTargetType? = null,
    @SerializedName("range")
    private val _range: Double? = null,
    @SerializedName("targetCondition")
    private val _targetCondition: ExpressionLike? = null,

    @SerializedName("requiredMoves")
    private val _requiredMoves: List<String>? = null,
    @SerializedName("requiredAbilities")
    private val _requiredAbilities: List<String>? = null,
    @SerializedName("requiredTypes")
    private val _requiredTypes: List<String>? = null,
    @SerializedName("requiredSpecies")
    private val _requiredSpecies: List<String>? = null,

    @SerializedName("hitDelay")
    private val _hitDelay: ExpressionLike? = null,
    @SerializedName("hitEffect")
    private val _hitEffect: ResourceLocation? = null,
    @SerializedName("fallbackActionEffect")
    private val _fallbackActionEffect: ResourceLocation? = null,

    @SerializedName("variables")
    private val _variables: Map<String, Any>? = null,
) {

    @Transient
    lateinit var pokeSkill: PokeSkill

    @Transient
    var tier: Int = -1

    fun initialize(skill: PokeSkill, tier: Int): PokeSkillTier {
        this.pokeSkill = skill
        this.tier = tier
        return this
    }

    companion object {
        private val DEFAULT_ACTION_EFFECT = cobblemonResource("default_poke_skill")
    }

    val id: ResourceLocation
        get() = pokeSkill.id
    val name: String
        get() = pokeSkill.name

    val enabled: Boolean
        get() = _enabled ?: pokeSkill.enabled ?: true
    val actionEffect: ActionEffectTimeline?
        get() = _actionEffect ?: pokeSkill.actionEffect
    val icon: ResourceLocation?
        get() = _icon ?: pokeSkill.icon
    val cooldownTicks: Int
        get() = _cooldownTicks ?: pokeSkill.cooldownTicks ?: 0
    val accessCondition: ExpressionLike
        get() = _accessCondition ?: pokeSkill.accessCondition ?: "true".asExpressionLike()
    val useCondition: ExpressionLike
        get() = _useCondition ?: pokeSkill.useCondition ?: "true".asExpressionLike()

    val targetType: PokeSkillTargetType?
        get() = _targetType ?: pokeSkill.targetType
    val range: Double?
        get() = _range ?: pokeSkill.range
    val targetCondition: ExpressionLike?
        get() = _targetCondition ?: pokeSkill.targetCondition

    val requiredMoves: List<String>?
        get() = _requiredMoves ?: pokeSkill.requiredMoves
    val requiredAbilities: List<String>?
        get() = _requiredAbilities ?: pokeSkill.requiredAbilities
    val requiredTypes: List<String>?
        get() = _requiredTypes ?: pokeSkill.requiredTypes
    val requiredSpecies: List<String>?
        get() = _requiredSpecies ?: pokeSkill.requiredSpecies

    val hitDelay: ExpressionLike?
        get() = _hitDelay ?: pokeSkill.hitDelay
    val hitEffect: ResourceLocation?
        get() = _hitEffect ?: pokeSkill.hitEffect
    val fallbackActionEffect: ResourceLocation?
        get() = _fallbackActionEffect ?: pokeSkill.fallbackActionEffect

    val variables: Map<String, Any>?
        get() = _variables ?: pokeSkill.variables

    /**
     * Determines access by checking the pokemons data against the granting lists.
     * If no granting lists are populated, accessCondition is used as a fallback.
     */
    fun canAccess(player: Player, pokemon: Pokemon): Boolean {
        if (!enabled) {
            return false
        }

        val requiredMoves = requiredMoves
        val requiredAbilities = requiredAbilities
        val requiredTypes = requiredTypes
        val requiredSpecies = requiredSpecies

        // Check granting lists — if any are populated, check if the player has any of the listed moves/abilities/types/species
        val hasGrantingLists = !requiredMoves.isNullOrEmpty() || !requiredAbilities.isNullOrEmpty() ||
                !requiredTypes.isNullOrEmpty() || !requiredSpecies.isNullOrEmpty()

        if (hasGrantingLists) {
            val moveMatch = !requiredMoves.isNullOrEmpty() && requiredMoves.any { moveName ->
                pokemon.moveSet.any { it.template.name == moveName }
            }

            val abilityMatch = !requiredAbilities.isNullOrEmpty() && requiredAbilities.any { abilityName ->
                pokemon.ability.name == abilityName
            }

            val typeMatch = !requiredTypes.isNullOrEmpty() && requiredTypes.any { typeName ->
                pokemon.types.any { it.name.equals(typeName, ignoreCase = true) }
            }

            val speciesMatch = !requiredSpecies.isNullOrEmpty() && requiredSpecies.any { speciesName ->
                pokemon.species.name.equals(speciesName, ignoreCase = true)
            }

            if (!(moveMatch || abilityMatch || typeMatch || speciesMatch)) return false
        }

        // Always evaluate accessCondition — acts as additional gate when granting lists are present,
        // or as the sole gate when no granting lists are populated
        runtime.withQueryValue("player", player.asMostSpecificMoLangValue())
        runtime.withQueryValue("pokemon", pokemon.struct)
        pokemon.entity?.let { runtime.withQueryValue("entity", it.asMostSpecificMoLangValue()) }
        return runtime.resolveBoolean(accessCondition)
    }

    fun canUse(player: Player, pokemon: Pokemon): Boolean {
        val status = pokemon.status?.status
        if ((!Cobblemon.config.infinitePokeSkillCooldown && id in pokemon.skillCooldownsTicks) || player.isInBattle() || status == Statuses.SLEEP || status == Statuses.FROZEN) {
            return false
        }
        runtime.withQueryValue("player", player.asMostSpecificMoLangValue())
        runtime.withQueryValue("pokemon", pokemon.struct)
        val canUse = runtime.resolveBoolean(useCondition)
        return canUse
    }

    /**
     * Resolves which move from the pokemon's moveset grants this skill.
     * Returns the first matching move name from requiredMoves, or null if none match.
     */
    fun resolveGrantingMove(pokemon: Pokemon): String? {
        val requiredMoves = requiredMoves
        if (requiredMoves.isNullOrEmpty()) return null  // need this guard
        return requiredMoves.firstOrNull { moveName ->
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
        val effectiveTargetType = targetType ?: PokeSkillTargetType.NONE
        val effectiveRange = range ?: 8.0

        // Resolve the action effect to run
        val actionEffect = actionEffect
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
        skillRuntime.environment.query.addFunction("missed") { DoubleValue(0.0) } // move action effects use this to determine if the target particles should play. We want them to always play for skills I believe
        injectVariables(skillRuntime)

        // Targeting — raycast based on targetType
        when (effectiveTargetType) {
            PokeSkillTargetType.SELF -> {
                injectEntityTarget(skillRuntime, player)
                skillRuntime.environment.setSimpleVariable("target_is_entity", DoubleValue(1.0))
            }
            PokeSkillTargetType.BLOCK -> {
                val blockHit = getPlayerLookedBlock(player, effectiveRange) ?: return false
                injectBlockTarget(skillRuntime, blockHit, level)
                skillRuntime.environment.setSimpleVariable("target_is_entity", DoubleValue(0.0))
            }
            PokeSkillTargetType.ENTITY -> {
                val entityHit = getPlayerLookedEntity(player, effectiveRange) ?: return false
                injectEntityTarget(skillRuntime, entityHit)
                skillRuntime.environment.setSimpleVariable("target_is_entity", DoubleValue(1.0))
            }
            PokeSkillTargetType.BOTH -> {
                val entityHit = getPlayerLookedEntity(player, effectiveRange)
                if (entityHit != null) {
                    injectEntityTarget(skillRuntime, entityHit)
                    skillRuntime.environment.setSimpleVariable("target_is_entity", DoubleValue(1.0))
                } else {
                    val blockHit = getPlayerLookedBlock(player, effectiveRange) ?: return false
                    injectBlockTarget(skillRuntime, blockHit, level)
                    skillRuntime.environment.setSimpleVariable("target_is_entity", DoubleValue(0.0))
                }
            }
            PokeSkillTargetType.NONE -> {}
        }

        // Evaluate target condition
        val targetCondition = targetCondition
        if (targetCondition != null && !skillRuntime.resolveBoolean(targetCondition)) return false

        // Inject wrapper skill variables
        val visualEffect = resolveVisualEffect(pokemon)
        if (visualEffect != null) {
            skillRuntime.environment.setSimpleVariable("granting_move", StringValue(visualEffect.toString()))
        }

        if (hitEffect != null) {
            skillRuntime.environment.setSimpleVariable("hit_effect", StringValue(hitEffect.toString()))
        }

        val hitDelay = hitDelay
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
            if (!Cobblemon.config.infinitePokeSkillCooldown) {
                pokemon.skillCooldownsTicks[id] = cooldownTicks
            }
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

    private fun injectVariables(runtime: MoLangRuntime) {
        variables?.forEach { (key, value) ->
            runtime.environment.setSimpleVariable(
                key,
                value.asMostSpecificMoLangValue()
            )
        }
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
        val entityHitResult = ProjectileUtil.getEntityHitResult(
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
