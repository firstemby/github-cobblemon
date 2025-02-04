package com.cobblemon.mod.common.pokemon.transformation.proxies

import com.cobblemon.mod.common.api.pokemon.transformation.controller.TransformationController
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.controller.*
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
) : CobblemonTransformationProxy<FormDisplay, TemporaryForm, ClientFormController.Intermediate, ServerFormController.Intermediate>(pokemon) {

    override var clientController: CobblemonClientTransformationController<FormDisplay, ClientFormController.Intermediate> =
        ClientFormController(this.pokemon, emptySet())

    override var serverController: CobblemonServerTransformationController<TemporaryForm, ServerFormController.Intermediate> =
        ServerFormController(this.pokemon, emptySet(), emptySet())
}