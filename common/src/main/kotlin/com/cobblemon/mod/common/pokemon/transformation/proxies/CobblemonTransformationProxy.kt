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
import com.cobblemon.mod.common.api.pokemon.transformation.proxy.PreProcessor
import com.cobblemon.mod.common.api.pokemon.transformation.proxy.TransformationProxy
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.controller.CobblemonClientTransformationController
import com.cobblemon.mod.common.pokemon.transformation.controller.CobblemonServerTransformationController

/**
 * Default holder of [TransformationProxy]s.
 *
 * @author Licious, Segfault Guy
 * @since January 19th, 2025
 */
abstract class CobblemonTransformationProxy<C : TransformationDisplay, S : Transformation, CI : PreProcessor, SI : PreProcessor>(
    private val pokemon: Pokemon,
) : TransformationProxy<C, S, CI, SI> {

    protected abstract var clientController: CobblemonClientTransformationController<C, CI>
    protected abstract var serverController: CobblemonServerTransformationController<S, SI>

    override fun isClient(): Boolean = this.pokemon.isClient

    override fun current(): TransformationController<out TransformationLike, *> = if (this.isClient()) this.clientController else this.serverController

    override fun client(): CobblemonClientTransformationController<C, CI> {
        if (!this.isClient()) {
            throw ClassCastException("Cannot use the client implementation from the server side")
        }
        return this.clientController
    }

    override fun server(): CobblemonServerTransformationController<S, SI> {
        if (this.isClient()) {
            throw ClassCastException("Cannot use the server implementation from the client side")
        }
        return this.serverController
    }

    internal fun overrideController(newInstance: TransformationController<out TransformationLike, PreProcessor>) {
        when (newInstance) {
            is CobblemonClientTransformationController -> {
                this.clientController = newInstance as CobblemonClientTransformationController<C, CI>
            }
            is CobblemonServerTransformationController -> {
                this.serverController = newInstance as CobblemonServerTransformationController<S, SI>
            }
            else -> {
                throw IllegalArgumentException("Cannot resolve override of type ${newInstance::class.simpleName}")
            }
        }
    }
}