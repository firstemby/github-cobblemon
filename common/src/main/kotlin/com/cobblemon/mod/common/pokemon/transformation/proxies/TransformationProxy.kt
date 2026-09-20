/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.proxies

import com.cobblemon.mod.common.api.pokemon.transformation.Transformation
import com.cobblemon.mod.common.api.pokemon.transformation.TransformationDisplay
import com.cobblemon.mod.common.api.pokemon.transformation.TransformationLike
import com.cobblemon.mod.common.api.pokemon.transformation.controller.TransformationController
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.controller.ClientTransformationController
import com.cobblemon.mod.common.pokemon.transformation.controller.ServerTransformationController

/**
 * Responsible for holding the different [TransformationController] implementations based on the logical side.
 * If you haven't already checked your side previously you can use [isClient].
 *
 * @param C The type of [TransformationDisplay] on the client side.
 * @param S The type of [Transformation] on the server side.
 *
 * @author Licious, Segfault Guy
 * @since January 19th, 2025
 */
abstract class TransformationProxy<C : TransformationDisplay, S : Transformation>(
    private val pokemon: Pokemon,
) {
    protected abstract var clientController: ClientTransformationController<C>
    protected abstract var serverController: ServerTransformationController<S>

    /** @return whether this proxy is on the client or server. */
    fun isClient(): Boolean = this.pokemon.isClient

    /** @return the [TransformationController] of this controller matching the current side. */
    fun current(): TransformationController<out TransformationLike> = if (this.isClient()) this.clientController else this.serverController

    /** @return the [ClientTransformationController] belonging to this proxy. */
    fun client(): ClientTransformationController<C> {
        if (!this.isClient()) {
            throw ClassCastException("Cannot use the client implementation from the server side")
        }
        return this.clientController
    }

    /** @return the [ServerTransformationController] belonging to this proxy. */
    fun server(): ServerTransformationController<S> {
        if (this.isClient()) {
            throw ClassCastException("Cannot use the server implementation from the client side")
        }
        return this.serverController
    }

    /**
     * Replaces the existing [ServerTransformationController] or [ClientTransformationController].
     *
     * NOTE: the [TransformationLike] and intermediary generic types of [newInstance] are not checked against the types
     * of the existing controller. They are cast to the types of this proxy. YOU HAVE BEEN WARNED!
     */
    internal fun overrideController(newInstance: TransformationController<out TransformationLike>) {
        when (newInstance) {
            is ClientTransformationController -> { this.clientController = newInstance as ClientTransformationController<C> }
            is ServerTransformationController<*> -> { this.serverController = newInstance as ServerTransformationController<S> }
            else -> { throw IllegalArgumentException("Cannot resolve override of type ${newInstance::class.simpleName}") }
        }
    }
}