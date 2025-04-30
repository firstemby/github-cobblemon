package com.cobblemon.mod.common.pokemon.transformation.form

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.molang.MoLangFunctions.addStandardFunctions
import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext
import com.cobblemon.mod.common.api.moves.animations.ActionEffectTimeline
import com.cobblemon.mod.common.api.moves.animations.ActionEffects
import com.cobblemon.mod.common.api.moves.animations.UsersProvider
import com.cobblemon.mod.common.api.pokemon.PokemonPropertyExtractor
import com.cobblemon.mod.common.api.pokemon.transformation.Transformation
import com.cobblemon.mod.common.api.pokemon.transformation.evolution.PreEvolution
import com.cobblemon.mod.common.api.pokemon.transformation.requirement.TransformationRequirement
import com.cobblemon.mod.common.api.pokemon.transformation.trigger.ContextTrigger
import com.cobblemon.mod.common.api.pokemon.transformation.trigger.TransformationTrigger
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.pokemon.transformation.evolution.Evolution
import com.cobblemon.mod.common.pokemon.transformation.triggers.ItemInteractionTrigger
import com.cobblemon.mod.common.pokemon.transformation.triggers.LevelUpTrigger
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import net.minecraft.resources.ResourceLocation

/**
 * A form of a [Species] that is interchangeable with a parent [PermanentForm]. This type of form is temporary and may be toggled
 * OUTSIDE of battle by a [TransformationTrigger] when the respective [TransformationRequirement]s are satisfied.
 *
 * Sample forms:
 * https://bulbapedia.bulbagarden.net/wiki/Mega_Evolution
 * https://bulbapedia.bulbagarden.net/wiki/Rotom#Forms
 *
 * Note: triggering this type of form INSIDE a battle is instructed by Showdown.
 *
 * @author Segfault Guy
 * @since October 21st, 2023
 */
open class TemporaryForm (
    /** Whether this form is triggered during a battle. */
    val battleOnly: Boolean = false,
    /** Whether this form can be freely toggled after requirements are initially met. */
    val toggleable: Boolean = false,
    /** */
    val formChangeEffect: ResourceLocation? = null,
    /** The [TransformationTrigger] to change to this form OUTSIDE of battle. */
    override val trigger: TransformationTrigger = LevelUpTrigger(),
    /** The [TransformationRequirement]s that need to be satisfied for this form OUTSIDE of battle. */
    override val requirements: Set<TransformationRequirement> = mutableSetOf(),
    /** If this form can be deferred and started whenever rather than immediately on trigger. */
    override val optional: Boolean = false,
    /** If this form needs its requirements reevaluated to remain valid. */
    override val reevaluate: Boolean = true,
) : FormData(), Transformation {

    override val id: String get() = this.formOnlyShowdownId()

    /* Temporary forms DO NOT have their own preevo/evolutions/forms and have to fallback on the parent. Because parentForm
       is not initialized until AFTER species registration, none of these properties are safe to access until [PokemonSpecies.observable] */

    // preevo is the same as the parent
    override val preEvolution: PreEvolution? get() = parentForm.preEvolution

    // allows preevos to have their own form changes
    override val evolutions: MutableSet<Evolution> get() = parentForm.evolutions

    // allows for form changes between adjacent temporary forms belonging to the same parent
    override val changeableForms: Collection<TemporaryForm> get() = parentForm.changeableForms  // technically this includes itself

    @delegate:Transient
    override val parentForm: PermanentForm by lazy { parentFormInitializer ?:
        throw IllegalStateException("Unable to resolve parent of ${this.species.name}'s temporary form: ${this.name}") } // temp forms MUST have a parent

    // TODO FORM_TESTED event
    override fun test(pokemon: Pokemon, context: ContextTrigger.Context?) = pokemon.form !== this && super.test(pokemon, context)

    /**
     * Starts this form change or queues it if [toggleable] is true.
     *
     * @param pokemon The [Pokemon] changing to this form.
     * @param context The optional [TriggerContext] needed to change form.
     * @return Whether the [TemporaryForm] was successful.
     */
    override fun start(pokemon: Pokemon, context: ContextTrigger.Context?): Boolean {
        if (this.test(pokemon, context)) {
            return if (this.optional)
                pokemon.formProxy.server().add(this)
            else {
                this.forceStart(pokemon)
                true
            }
        }
        return false
    }

    val actionEffect = ActionEffects.actionEffects[name.asIdentifierDefaultingNamespace()]
        ?: ActionEffects.actionEffects["generic_form".asIdentifierDefaultingNamespace()] ?: throw Exception()

    // TODO FORM_COMPLETE event
    override fun forceStart(pokemon: Pokemon) {
        val pokemonEntity = pokemon.entity
        if (pokemonEntity != null) {
            val runtime = MoLangRuntime()
            runtime.environment.query.addStandardFunctions()
            val providers: MutableList<Any> = mutableListOf(UsersProvider(pokemonEntity))
            val context = ActionEffectContext(
                actionEffect = actionEffect,
                runtime = runtime,
                providers = providers,
                level = pokemonEntity.level()
            )
            this.forceStart(pokemonEntity, context)
        }
        else {
            pokemon.form = this
            super.forceStart(pokemon)
        }
    }

    fun forceStart(pokemonEntity: PokemonEntity, context: ActionEffectContext) {
        pokemonEntity.entityData.set(PokemonEntity.TRANSFORMATION_STARTED, true)
        val future = actionEffect.run(context)
        future.thenApply {
            pokemonEntity.pokemon.form = this
            pokemonEntity.entityData.set(PokemonEntity.TRANSFORMATION_STARTED, false)
            super.forceStart(pokemonEntity.pokemon)
        }
    }

    // TODO FORM_DISPLAY event
    override fun convertToDisplay(pokemon: Pokemon): FormDisplay {
        val result = pokemon.clone(newUUID = false).also { it.form = this } // TODO is this copy necessary? cant we just expose only aspects/species in the event???
        val expectedDisplay = FormDisplay(this.id, result)
        return expectedDisplay
    }
}
