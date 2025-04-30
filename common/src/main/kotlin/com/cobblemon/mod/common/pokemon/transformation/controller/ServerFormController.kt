package com.cobblemon.mod.common.pokemon.transformation.controller

import com.cobblemon.mod.common.api.pokemon.transformation.proxy.PreProcessor
import com.cobblemon.mod.common.api.pokemon.transformation.progress.TransformationProgress
import com.cobblemon.mod.common.api.pokemon.transformation.progress.TransformationProgressTypes
import com.cobblemon.mod.common.net.messages.client.pokemon.update.form.AddFormPacket
import com.cobblemon.mod.common.net.messages.client.pokemon.update.form.ClearFormsPacket
import com.cobblemon.mod.common.net.messages.client.pokemon.update.form.RemoveFormPacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.form.TemporaryForm
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

/**
 * Controller for managing changeable forms on the server. These are data objects in the form of [TemporaryForm]s.
 *
 * @author Segfault Guy
 * @since January 19th, 2025
 */
class ServerFormController(
    private val pokemon: Pokemon,
    formIds: Set<String>,
    progress: Set<TransformationProgress<*>>,
) : CobblemonServerTransformationController<TemporaryForm, ServerFormController.Intermediate>(pokemon, formIds, progress) {

    init {
        val potentialForms = this.pokemon.changeableForms.map { it.id }.toSet()
        this.transformationIds.removeIf { !potentialForms.contains(it.lowercase()) }
        this.transformationIds.forEach { this.findFormFromId(it)?.let(this::silentAdd) }
    }

    private fun findFormFromId(id: String): TemporaryForm? = this.pokemon.changeableForms.firstOrNull { form -> form.id.equals(id, true) }

    override fun addPacket(form: TemporaryForm) = AddFormPacket(this.pokemon, form)

    override fun clearPacket() = ClearFormsPacket { this.pokemon }

    override fun removePacket(form: TemporaryForm) = RemoveFormPacket(this.pokemon, form)

    // TODO form events

    override fun isDuplicate(element: TemporaryForm) = this.find { it.name == element.name } != null

    override fun asIntermediate(): Intermediate = Intermediate(this.transformations.map { it.id }.toSet(), this.progress)

    override fun revert() { if (this.canRevert()) this.pokemon.form = (this.pokemon.form as TemporaryForm).parentForm } // assignment already handles this

    override fun canRevert(): Boolean = pokemon.form is TemporaryForm && (pokemon.form as TemporaryForm).toggleable

    data class Intermediate(
        val formIds: Set<String>,
        val progress: Set<TransformationProgress<*>>,
    ): PreProcessor {
        override fun create(pokemon: Pokemon): ServerFormController = ServerFormController(
            pokemon,
            this.formIds,
            this.progress
        )
    }

    companion object {
        private const val PENDING = "pending"
        private const val PROGRESS = "progress"

        @JvmStatic
        val CODEC: Codec<Intermediate> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.list(Codec.STRING).fieldOf(PENDING).forGetter { controller -> controller.formIds.toMutableList() },
                Codec.list(TransformationProgressTypes.codec()).fieldOf(PROGRESS).forGetter { controller -> controller.progress.toMutableList() }
            ).apply(instance) { forms, progress -> Intermediate(forms.toSet(), progress.toSet()) }
        }
    }
}