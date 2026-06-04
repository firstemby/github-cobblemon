/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.form

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.pokemon.form.FormCompleteEvent
import com.cobblemon.mod.common.api.events.pokemon.form.FormDisplayEvent
import com.cobblemon.mod.common.api.events.pokemon.form.FormTestedEvent
import com.cobblemon.mod.common.api.molang.MoLangFunctions.addStandardFunctions
import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext
import com.cobblemon.mod.common.api.moves.animations.ActionEffectTimeline
import com.cobblemon.mod.common.api.moves.animations.ActionEffects
import com.cobblemon.mod.common.api.moves.animations.UsersProvider
import com.cobblemon.mod.common.api.pokemon.transformation.Transformation
import com.cobblemon.mod.common.api.pokemon.transformation.evolution.PreEvolution
import com.cobblemon.mod.common.api.pokemon.transformation.requirement.TransformationRequirement
import com.cobblemon.mod.common.api.pokemon.transformation.trigger.ContextTrigger
import com.cobblemon.mod.common.api.pokemon.transformation.trigger.TransformationTrigger
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.pokemon.activestate.ShoulderedState
import com.cobblemon.mod.common.pokemon.transformation.evolution.Evolution
import com.cobblemon.mod.common.pokemon.transformation.triggers.LevelUpTrigger
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.cobblemon.mod.common.util.cobblemonResource
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

    var actionEffectIdentifier: ResourceLocation? = null

    val actionEffect: ActionEffectTimeline
        get() = ActionEffects.actionEffects[actionEffectIdentifier ?: cobblemonResource("form_${name.lowercase()}")]
            ?: ActionEffects.actionEffects["generic_form".asIdentifierDefaultingNamespace()]
            ?: throw Exception()

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

    override fun test(pokemon: Pokemon, context: ContextTrigger.Context?): Boolean {
        val baseResult = pokemon.form !== this && super.test(pokemon, context)
        val event = FormTestedEvent(pokemon, this, baseResult)
        CobblemonEvents.FORM_TESTED.post(event)
        return event.result
    }

    /**
     * Starts this form change or queues it if [toggleable] is true.
     *
     * @param pokemon The [Pokemon] changing to this form.
     * @param context The optional [TriggerContext] needed to change form.
     * @return Whether the [TemporaryForm] was successful.
     */
    override fun tryStart(pokemon: Pokemon, context: ContextTrigger.Context?): Boolean {
        if (this.test(pokemon, context)) {
            return if (this.optional)
                pokemon.formProxy.server().add(this)
            else {
                this.perform(pokemon)
                true
            }
        }
        return false
    }

    override fun perform(pokemon: Pokemon) {
        if (pokemon.state is ShoulderedState) {
            pokemon.recall()
        }

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
        } else {
            fulfill(pokemon)
        }
    }

    override fun fulfill(pokemon: Pokemon) {
        super.fulfill(pokemon)
        pokemon.form = this
        pokemon.entity?.entityData?.set(PokemonEntity.TRANSFORMATION_STARTED, false)
        CobblemonEvents.FORM_COMPLETE.post(FormCompleteEvent(pokemon, this))
    }

    fun forceStart(pokemonEntity: PokemonEntity, context: ActionEffectContext) {
        pokemonEntity.entityData.set(PokemonEntity.TRANSFORMATION_STARTED, true)
        val finishTransformation: () -> Unit = {
            if (pokemonEntity.isTransforming) {
                fulfill(pokemonEntity.pokemon)
            }
        }
        context.holdRemovalCallbacks["transformation"] = finishTransformation
        val future = actionEffect.run(context)
        future.thenRun(finishTransformation)
    }

    fun revert(pokemon: Pokemon) {
        if (toggleable) {
            pokemon.form = parentForm
        }
    }

    override fun convertToDisplay(pokemon: Pokemon): FormDisplay {
        val species = pokemon.species
        val aspects = pokemon.aspects.toMutableSet()
        aspects.removeAll(pokemon.form.aspects.toSet())
        aspects.addAll(this.aspects)
        val expectedDisplay = FormDisplay(this.id, species, aspects)
        val event = FormDisplayEvent(pokemon, expectedDisplay, this)
        CobblemonEvents.FORM_DISPLAY.post(event)
        return event.display
    }
}
