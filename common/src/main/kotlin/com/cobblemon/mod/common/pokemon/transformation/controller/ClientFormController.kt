package com.cobblemon.mod.common.pokemon.transformation.controller

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.api.pokemon.transformation.proxy.PreProcessor
import com.cobblemon.mod.common.net.messages.server.pokemon.update.form.AcceptFormPacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.form.FormDisplay
import com.mojang.serialization.Codec

/**
 * Controller for managing changeable forms on the client.
 * From the client's perspective, these forms are visual only and are represented by [FormDisplay]s.
 *
 * @author Segfault Guy
 * @since January 19th, 2025
 */
class ClientFormController(
    pokemon: Pokemon,
    forms: Set<FormDisplay>,
) : CobblemonClientTransformationController<FormDisplay, ClientFormController.Intermediate>(pokemon, forms) {

    override fun notification() {
        // TODO
    }

    override fun acceptPacket(pokemon: Pokemon, form: FormDisplay): NetworkPacket<*> = AcceptFormPacket(pokemon, form)

    override fun asIntermediate(): Intermediate = Intermediate(this.transformations)

    data class Intermediate(val forms: Set<FormDisplay>): PreProcessor {
        override fun create(pokemon: Pokemon): ClientFormController = ClientFormController(pokemon, this.forms)
    }

    companion object {
        @JvmStatic
        val CODEC: Codec<Intermediate> = FormDisplay.CODEC.listOf()
            .xmap(
                { displays -> Intermediate(displays.toSet()) },
                { controller -> controller.forms.toMutableList() }
            )
    }
}