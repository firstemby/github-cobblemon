/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.controller

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.pokemon.form.FormAcceptedEvent
import com.cobblemon.mod.common.net.messages.client.pokemon.update.form.AddFormPacket
import com.cobblemon.mod.common.net.messages.client.pokemon.update.form.ClearFormsPacket
import com.cobblemon.mod.common.net.messages.client.pokemon.update.form.RemoveFormPacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.form.TemporaryForm

/**
 * Controller for managing changeable forms on the server. These are data objects in the form of [TemporaryForm]s.
 *
 * @author Segfault Guy
 * @since January 19th, 2025
 */
class ServerFormController(
    private val pokemon: Pokemon,
    formIds: Set<String>,
) : ServerTransformationController<TemporaryForm>(pokemon, formIds) {

    init {
        val potentialForms = this.pokemon.changeableForms.map { it.id }.toSet()
        this.transformationIds.removeIf { !potentialForms.contains(it.lowercase()) }
        this.transformationIds.forEach { this.findFormFromId(it)?.let(this::silentAdd) }
    }

    private fun findFormFromId(id: String): TemporaryForm? = this.pokemon.changeableForms.firstOrNull { form -> form.id.equals(id, true) }
    override fun addPacket(transformation: TemporaryForm) = AddFormPacket(this.pokemon, transformation)
    override fun clearPacket() = ClearFormsPacket { this.pokemon }
    override fun removePacket(transformation: TemporaryForm) = RemoveFormPacket(this.pokemon, transformation)
    override fun isDuplicate(element: TemporaryForm) = this.find { it.name == element.name } != null
    override fun canRevert(): Boolean = pokemon.form is TemporaryForm && (pokemon.form as TemporaryForm).toggleable

    override fun start(transformation: TemporaryForm) {
        if (!canStart(transformation)) return
        CobblemonEvents.FORM_ACCEPTED.postThen(
            event = FormAcceptedEvent(this.pokemon, transformation),
            ifSucceeded = { transformation.perform(this.pokemon) }
        )
    }
}