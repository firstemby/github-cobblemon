package com.cobblemon.mod.common.pokemon.transformation.proxies

import com.cobblemon.mod.common.api.pokemon.transformation.controller.TransformationController
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.controller.ClientFormController
import com.cobblemon.mod.common.pokemon.transformation.controller.ClientTransformationController
import com.cobblemon.mod.common.pokemon.transformation.controller.ServerFormController
import com.cobblemon.mod.common.pokemon.transformation.controller.ServerTransformationController
import com.cobblemon.mod.common.pokemon.transformation.form.FormDisplay
import com.cobblemon.mod.common.pokemon.transformation.form.TemporaryForm

/**
 * Holder of the client and server [TransformationController]s for form changes.
 *
 * @author Segfault Guy
 * @since January 19th, 2025
 */
class FormProxy(
    private val pokemon: Pokemon,
) : TransformationProxy<FormDisplay, TemporaryForm, ServerFormController.Intermediate>(pokemon) {

    override var clientController: ClientTransformationController<FormDisplay> =
        ClientFormController(this.pokemon, emptySet())

    override var serverController: ServerTransformationController<TemporaryForm, ServerFormController.Intermediate> =
        ServerFormController(this.pokemon, emptySet(), emptySet())
}